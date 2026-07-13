package ai.platform.aiassit.conversation.transport.sse;

import ai.platform.aiassit.conversation.protocol.ChatProtocolEventCursor;
import ai.platform.aiassit.conversation.protocol.ChatTransportProtocolAdapter;
import ai.platform.aiassit.conversation.protocol.dto.ChatEventEnvelope;
import ai.platform.aiassit.conversation.protocol.dto.ChatTransportRequest;
import ai.platform.aiassit.conversation.dto.chat.ConversationStreamReconnectRequest;
import ai.platform.aiassit.conversation.runtime.ConversationRunManager;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscriber;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscription;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.service.ConversationExecutionService;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;

@Component
@Slf4j
public class ProtocolSseConversationTransport {

    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private final ConversationRunManager runManager;
    private final ConversationExecutionService executionService;
    private final ChatTransportProtocolAdapter protocolAdapter;
    private final ChatProtocolEventCursor eventCursor;
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chat-event-v2-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public ProtocolSseConversationTransport(ConversationRunManager runManager,
                                            ConversationExecutionService executionService,
                                            ChatTransportProtocolAdapter protocolAdapter,
                                            ChatProtocolEventCursor eventCursor) {
        this.runManager = runManager;
        this.executionService = executionService;
        this.protocolAdapter = protocolAdapter;
        this.eventCursor = eventCursor;
    }

    public SseEmitter start(ConversationQueryCommand command) {
        ConversationRunSnapshot run = runManager.start(command);
        return subscribe(run.runId(), command.getUserId(), null);
    }

    public SseEmitter reconnect(ChatTransportRequest request, Long userId, String traceId) {
        String runId = request == null ? null : request.getRunId();
        String sessionCode = request == null ? null : request.getSessionCode();
        String roundCode = request == null ? null : request.getRoundCode();
        String lastEventId = request == null ? null : request.getLastEventId();

        Optional<ConversationRunSnapshot> run = hasRunLocator(runId, sessionCode, roundCode)
                ? runManager.find(runId, sessionCode, roundCode, userId)
                : Optional.empty();
        if (run.isPresent()) {
            log.info("chat-event.v2 reconnect matched runtime run, runId={}, sessionCode={}, roundCode={}, userId={}, lastEventId={}",
                    run.get().runId(), sessionCode, roundCode, userId, lastEventId);
            return subscribe(run.get().runId(), userId, lastEventId);
        }

        log.info("chat-event.v2 reconnect falling back to persisted replay, runId={}, sessionCode={}, roundCode={}, userId={}, lastEventId={}",
                runId, sessionCode, roundCode, userId, lastEventId);
        return replayPersisted(request, userId, traceId);
    }

    private SseEmitter subscribe(String runId, Long userId, String lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<ConversationRunSubscription> subscriptionRef = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> heartbeatRef = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        ConversationRunSubscriber subscriber = new ConversationRunSubscriber() {
            @Override
            public void onEvent(ConversationQueryStreamEvent event) throws IOException {
                for (ChatEventEnvelope envelope : protocolAdapter.adapt(event)) {
                    if (eventCursor.isAfter(envelope.getEventId(), lastEventId)) {
                        send(emitter, envelope);
                    }
                }
            }

            @Override
            public void onComplete() {
                completed.set(true);
                emitter.complete();
            }
        };
        try {
            ConversationRunSubscription subscription = runManager.subscribe(
                    runId, userId, eventCursor.runtimeReplayCursor(lastEventId), subscriber);
            subscriptionRef.set(subscription);
            Runnable close = () -> {
                ConversationRunSubscription current = subscriptionRef.getAndSet(null);
                if (current != null) {
                    current.close();
                }
                ScheduledFuture<?> heartbeat = heartbeatRef.getAndSet(null);
                if (heartbeat != null) {
                    heartbeat.cancel(false);
                }
            };
            emitter.onCompletion(close);
            emitter.onTimeout(close);
            emitter.onError(error -> close.run());
            ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | RuntimeException ex) {
                    close.run();
                    try {
                        emitter.completeWithError(ex);
                    } catch (RuntimeException ignored) {
                        // The emitter may already be complete; resources are closed above.
                    }
                }
            }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
            heartbeatRef.set(heartbeat);
            if (completed.get()) {
                close.run();
            }
        } catch (RuntimeException ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    private SseEmitter replayPersisted(ChatTransportRequest request, Long userId, String traceId) {
        SseEmitter emitter = new SseEmitter(0L);
        ConversationStreamReconnectRequest replayRequest = toReplayRequest(request);
        String runId = request == null ? null : request.getRunId();
        String lastEventId = request == null ? null : request.getLastEventId();
        try {
            List<ConversationQueryStreamEvent> events = executionService.replayStream(replayRequest, userId, traceId);
            for (int i = 0; i < events.size(); i++) {
                ConversationQueryStreamEvent event = events.get(i);
                if (!StringUtils.hasText(event.getRunId()) && StringUtils.hasText(runId)) {
                    event.setRunId(runId);
                }
                event.setEventId(eventCursor.persistedReplayEventId(event.getEventId(), lastEventId, i + 1));
                for (ChatEventEnvelope envelope : protocolAdapter.adapt(event)) {
                    send(emitter, envelope);
                }
            }
            emitter.complete();
            log.info("chat-event.v2 persisted replay completed, runId={}, sessionCode={}, roundCode={}, userId={}, eventCount={}",
                    runId, replayRequest.getSessionCode(), replayRequest.getRoundCode(), userId, events.size());
        } catch (Exception ex) {
            log.warn("chat-event.v2 persisted replay failed, runId={}, sessionCode={}, roundCode={}, userId={}",
                    runId, replayRequest.getSessionCode(), replayRequest.getRoundCode(), userId, ex);
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    private ConversationStreamReconnectRequest toReplayRequest(ChatTransportRequest request) {
        ConversationStreamReconnectRequest replayRequest = new ConversationStreamReconnectRequest();
        if (request != null) {
            replayRequest.setRunId(request.getRunId());
            replayRequest.setLastEventId(request.getLastEventId());
            replayRequest.setSessionCode(request.getSessionCode());
            replayRequest.setRoundCode(request.getRoundCode());
        }
        return replayRequest;
    }

    private boolean hasRunLocator(String runId, String sessionCode, String roundCode) {
        return StringUtils.hasText(runId)
                || (StringUtils.hasText(sessionCode) && StringUtils.hasText(roundCode));
    }

    private void send(SseEmitter emitter, ChatEventEnvelope envelope) throws IOException {
        emitter.send(SseEmitter.event()
                .id(envelope.getEventId())
                .name(envelope.getEventType())
                .data(envelope, MediaType.APPLICATION_JSON));
    }

    @PreDestroy
    void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
    }
}
