package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.protocol.dto.ChatTransportRequest;
import ai.platform.aiassit.conversation.runtime.ConversationRunManager;
import ai.platform.aiassit.conversation.service.ConversationProtocolQueryService;
import ai.platform.aiassit.conversation.support.ConversationCommandFactory;
import ai.platform.aiassit.conversation.support.ConversationRequestContextResolver;
import ai.platform.aiassit.conversation.transport.sse.ProtocolSseConversationTransport;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatTransportProtocolControllerTest {

    private final ProtocolSseConversationTransport transport = mock(ProtocolSseConversationTransport.class);
    private final ConversationRequestContextResolver contextResolver = mock(ConversationRequestContextResolver.class);
    private final ChatTransportProtocolController controller = new ChatTransportProtocolController(
            transport,
            mock(ConversationCommandFactory.class),
            contextResolver,
            mock(ConversationProtocolQueryService.class),
            mock(ConversationRunManager.class));

    @Test
    void usesLastEventIdHeaderWhenRequestBodyDoesNotProvideCursor() {
        ChatTransportRequest request = new ChatTransportRequest();
        SseEmitter emitter = new SseEmitter();
        when(contextResolver.currentUserId()).thenReturn(7L);
        when(contextResolver.traceId()).thenReturn("trace-1");
        when(transport.reconnect(same(request), eq(7L), eq("trace-1"))).thenReturn(emitter);

        controller.reconnect(request, "8.2");

        verify(transport).reconnect(same(request), eq(7L), eq("trace-1"));
        org.assertj.core.api.Assertions.assertThat(request.getLastEventId()).isEqualTo("8.2");
    }
}
