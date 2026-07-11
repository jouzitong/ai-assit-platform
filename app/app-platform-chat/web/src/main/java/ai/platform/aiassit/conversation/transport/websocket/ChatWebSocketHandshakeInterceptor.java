package ai.platform.aiassit.conversation.transport.websocket;

import ai.platform.aiassit.conversation.support.ConversationRequestContextResolver;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class ChatWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = "chatUserId";

    private final ConversationRequestContextResolver contextResolver;

    public ChatWebSocketHandshakeInterceptor(ConversationRequestContextResolver contextResolver) {
        this.contextResolver = contextResolver;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        attributes.put(USER_ID_ATTRIBUTE, contextResolver.currentUserId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // No handshake resource needs explicit cleanup.
    }
}
