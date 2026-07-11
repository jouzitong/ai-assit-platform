package ai.platform.aiassit.conversation.transport.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ChatWebSocketConfiguration implements WebSocketConfigurer {

    private final ChatWebSocketHandler handler;
    private final ChatWebSocketHandshakeInterceptor handshakeInterceptor;

    public ChatWebSocketConfiguration(ChatWebSocketHandler handler,
                                      ChatWebSocketHandshakeInterceptor handshakeInterceptor) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat")
                .addInterceptors(handshakeInterceptor);
    }
}
