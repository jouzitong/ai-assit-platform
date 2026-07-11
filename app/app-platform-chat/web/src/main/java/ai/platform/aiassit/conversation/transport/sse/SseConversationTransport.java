package ai.platform.aiassit.conversation.transport.sse;

import ai.platform.aiassit.conversation.dto.chat.ConversationStreamReconnectRequest;
import ai.platform.aiassit.conversation.runtime.ConversationRunManager;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscriber;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscription;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.service.ConversationExecutionService;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SseConversationTransport {

    private final ConversationRunManager runManager;
    private final ConversationExecutionService executionService;

    public SseConversationTransport(ConversationRunManager runManager,
                                    ConversationExecutionService executionService) {
        this.runManager = runManager;
        this.executionService = executionService;
    }

    public SseEmitter start(ConversationQueryCommand command) {
        ConversationRunSnapshot run = runManager.start(command);
        return subscribe(run.runId(), command.getUserId(), null);
    }

    public SseEmitter reconnect(ConversationStreamReconnectRequest request, Long userId, String traceId) {
        String runId = request == null ? null : request.getRunId();
        String sessionCode = request == null ? null : request.getSessionCode();
        String roundCode = request == null ? null : request.getRoundCode();
        String lastEventId = request == null ? null : request.getLastEventId();
        Optional<ConversationRunSnapshot> run = runManager.find(runId, sessionCode, roundCode, userId);
        if (run.isPresent()) {
            return subscribe(run.get().runId(), userId, lastEventId);
        }
        return replayPersisted(request, userId, traceId);
    }

    private SseEmitter subscribe(String runId, Long userId, String lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<ConversationRunSubscription> subscriptionRef = new AtomicReference<>();
        ConversationRunSubscriber subscriber = new ConversationRunSubscriber() {
            @Override
            public void onEvent(ConversationQueryStreamEvent event) throws IOException {
                send(emitter, event);
            }

            @Override
            public void onComplete() {
                emitter.complete();
            }
        };
        try {
            ConversationRunSubscription subscription = runManager.subscribe(runId, userId, lastEventId, subscriber);
            subscriptionRef.set(subscription);
            Runnable closeSubscription = () -> {
                ConversationRunSubscription current = subscriptionRef.getAndSet(null);
                if (current != null) {
                    current.close();
                }
            };
            emitter.onCompletion(closeSubscription);
            emitter.onTimeout(closeSubscription);
            emitter.onError(error -> closeSubscription.run());
        } catch (RuntimeException ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    private SseEmitter replayPersisted(ConversationStreamReconnectRequest request, Long userId, String traceId) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            List<ConversationQueryStreamEvent> events = executionService.replayStream(request, userId, traceId);
            for (ConversationQueryStreamEvent event : events) {
                send(emitter, event);
            }
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    private void send(SseEmitter emitter, ConversationQueryStreamEvent event) throws IOException {
        String eventType = StringUtils.hasText(event.getEventType()) ? event.getEventType() : "message";
        SseEmitter.SseEventBuilder builder = SseEmitter.event()
                .name(eventType)
                .data(event, MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(event.getEventId())) {
            builder.id(event.getEventId());
        }
        emitter.send(builder);
    }
}
