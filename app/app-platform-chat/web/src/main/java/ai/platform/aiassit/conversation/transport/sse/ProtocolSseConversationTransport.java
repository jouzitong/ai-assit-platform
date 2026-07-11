package ai.platform.aiassit.conversation.transport.sse;

import ai.platform.aiassit.conversation.protocol.ChatProtocolEventCursor;
import ai.platform.aiassit.conversation.protocol.ChatTransportProtocolAdapter;
import ai.platform.aiassit.conversation.protocol.dto.ChatEventEnvelope;
import ai.platform.aiassit.conversation.runtime.ConversationRunManager;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscriber;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscription;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ProtocolSseConversationTransport {

    private final ConversationRunManager runManager;
    private final ChatTransportProtocolAdapter protocolAdapter;
    private final ChatProtocolEventCursor eventCursor;

    public ProtocolSseConversationTransport(ConversationRunManager runManager,
                                            ChatTransportProtocolAdapter protocolAdapter,
                                            ChatProtocolEventCursor eventCursor) {
        this.runManager = runManager;
        this.protocolAdapter = protocolAdapter;
        this.eventCursor = eventCursor;
    }

    public SseEmitter start(ConversationQueryCommand command) {
        ConversationRunSnapshot run = runManager.start(command);
        return subscribe(run.runId(), command.getUserId(), null);
    }

    public SseEmitter reconnect(String runId, String lastEventId, Long userId) {
        return subscribe(runId, userId, lastEventId);
    }

    private SseEmitter subscribe(String runId, Long userId, String lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<ConversationRunSubscription> subscriptionRef = new AtomicReference<>();
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
            };
            emitter.onCompletion(close);
            emitter.onTimeout(close);
            emitter.onError(error -> close.run());
        } catch (RuntimeException ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    private void send(SseEmitter emitter, ChatEventEnvelope envelope) throws IOException {
        emitter.send(SseEmitter.event()
                .id(envelope.getEventId())
                .name(envelope.getEventType())
                .data(envelope, MediaType.APPLICATION_JSON));
    }
}
