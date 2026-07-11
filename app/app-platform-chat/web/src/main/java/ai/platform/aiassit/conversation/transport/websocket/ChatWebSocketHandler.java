package ai.platform.aiassit.conversation.transport.websocket;

import ai.platform.aiassit.conversation.protocol.ChatProtocolEventCursor;
import ai.platform.aiassit.conversation.protocol.ChatTransportProtocolAdapter;
import ai.platform.aiassit.conversation.protocol.dto.ChatEventEnvelope;
import ai.platform.aiassit.conversation.protocol.dto.ChatTransportRequest;
import ai.platform.aiassit.conversation.runtime.ConversationRunManager;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscriber;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscription;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.support.ConversationCommandFactory;
import ai.platform.aiassit.conversation.support.ConversationRequestContextResolver;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ConversationRunManager runManager;
    private final ConversationCommandFactory commandFactory;
    private final ConversationRequestContextResolver contextResolver;
    private final ChatTransportProtocolAdapter protocolAdapter;
    private final ChatProtocolEventCursor eventCursor;
    private final ObjectMapper objectMapper;
    private final Map<String, ConversationRunSubscription> subscriptions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ConversationRunManager runManager,
                                ConversationCommandFactory commandFactory,
                                ConversationRequestContextResolver contextResolver,
                                ChatTransportProtocolAdapter protocolAdapter,
                                ChatProtocolEventCursor eventCursor,
                                ObjectMapper objectMapper) {
        this.runManager = runManager;
        this.commandFactory = commandFactory;
        this.contextResolver = contextResolver;
        this.protocolAdapter = protocolAdapter;
        this.eventCursor = eventCursor;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ChatTransportRequest request = objectMapper.readValue(message.getPayload(), ChatTransportRequest.class);
        String type = request.getType();
        if ("chat.user_message".equals(type)) {
            start(session, request);
            return;
        }
        if ("chat.reconnect".equals(type)) {
            subscribe(session, request.getRunId(), request.getLastEventId());
            return;
        }
        if ("chat.stop".equals(type)) {
            runManager.cancel(request.getRunId(), request.getSessionCode(), request.getRoundCode(), userId(session));
            return;
        }
        sendError(session, request, "unsupported websocket message type: " + type);
    }

    private void start(WebSocketSession session, ChatTransportRequest request) throws IOException {
        ConversationQueryCommand command = commandFactory.fromProtocol(
                request, null, userId(session), contextResolver.newTraceId());
        ConversationRunSnapshot run = runManager.start(command);
        subscribe(session, run.runId(), null);
    }

    private void subscribe(WebSocketSession session, String runId, String lastEventId) throws IOException {
        if (!StringUtils.hasText(runId)) {
            sendError(session, null, "runId is required");
            return;
        }
        closeSubscription(session.getId());
        AtomicBoolean completed = new AtomicBoolean(false);
        ConversationRunSubscription subscription = runManager.subscribe(
                runId,
                userId(session),
                eventCursor.runtimeReplayCursor(lastEventId),
                new ConversationRunSubscriber() {
                    @Override
                    public void onEvent(ConversationQueryStreamEvent event) throws IOException {
                        for (ChatEventEnvelope envelope : protocolAdapter.adapt(event)) {
                            if (eventCursor.isAfter(envelope.getEventId(), lastEventId)) {
                                send(session, envelope);
                            }
                        }
                    }

                    @Override
                    public void onComplete() {
                        completed.set(true);
                        subscriptions.remove(session.getId());
                    }
                }
        );
        subscriptions.put(session.getId(), subscription);
        if (completed.get()) {
            closeSubscription(session.getId());
        }
    }

    private void send(WebSocketSession session, ChatEventEnvelope envelope) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
            }
        }
    }

    private void sendError(WebSocketSession session, ChatTransportRequest request, String message) throws IOException {
        ChatEventEnvelope error = new ChatEventEnvelope();
        error.setEventId("transport-" + System.nanoTime());
        error.setEventType("transport.error");
        error.setRunId(request == null ? null : request.getRunId());
        error.setRequestId(request == null ? null : request.getRequestId());
        error.setSessionCode(request == null ? null : request.getSessionCode());
        error.setRoundCode(request == null ? null : request.getRoundCode());
        error.setTimestamp(Instant.now().toString());
        error.setPayload(Map.of("message", message));
        send(session, error);
    }

    private Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get(ChatWebSocketHandshakeInterceptor.USER_ID_ATTRIBUTE);
        if (value instanceof Long userId) {
            return userId;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("authenticated chat user is required");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeSubscription(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        closeSubscription(session.getId());
    }

    private void closeSubscription(String sessionId) {
        ConversationRunSubscription subscription = subscriptions.remove(sessionId);
        if (subscription != null) {
            subscription.close();
        }
    }
}
