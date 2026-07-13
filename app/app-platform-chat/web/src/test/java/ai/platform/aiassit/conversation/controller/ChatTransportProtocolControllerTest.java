package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.protocol.ChatTransportProtocolAdapter;
import ai.platform.aiassit.conversation.protocol.dto.ChatTransportRequest;
import ai.platform.aiassit.conversation.runtime.ConversationRunManager;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunState;
import ai.platform.aiassit.conversation.service.ConversationProtocolQueryService;
import ai.platform.aiassit.conversation.support.ConversationCommandFactory;
import ai.platform.aiassit.conversation.support.ConversationRequestContextResolver;
import ai.platform.aiassit.conversation.transport.sse.ProtocolSseConversationTransport;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatTransportProtocolControllerTest {

    private final ProtocolSseConversationTransport transport = mock(ProtocolSseConversationTransport.class);
    private final ConversationRequestContextResolver contextResolver = mock(ConversationRequestContextResolver.class);
    private final ConversationRunManager runManager = mock(ConversationRunManager.class);
    private final ChatTransportProtocolController controller = new ChatTransportProtocolController(
            transport,
            mock(ConversationCommandFactory.class),
            contextResolver,
            mock(ConversationProtocolQueryService.class),
            runManager,
            new ChatTransportProtocolAdapter());

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

    @Test
    @SuppressWarnings("unchecked")
    void returnsSanitizedErrorSummaryAndStructuredErrorInfoForRunRecovery() {
        ConversationRunSnapshot run = new ConversationRunSnapshot(
                "run-1", "node-1", "trace-1", 7L, "session-1", "round-1",
                ConversationRunState.FAILED, Instant.now(), Instant.now(), Instant.now(),
                "invalid API key Authorization: Bearer sk-secret123 token=raw-token");
        when(contextResolver.currentUserId()).thenReturn(7L);
        when(runManager.find("run-1", null, null, 7L)).thenReturn(Optional.of(run));

        Map<String, Object> status = controller.runStatus("run-1");

        assertThat((String) status.get("error"))
                .doesNotContain("sk-secret123", "raw-token")
                .contains("Authorization: ***", "token=***");
        assertThat(status.get("errorInfo")).isInstanceOf(Map.class);
        Map<String, Object> errorInfo = (Map<String, Object>) status.get("errorInfo");
        assertThat(errorInfo)
                .containsEntry("code", "MODEL_CREDENTIAL_INVALID")
                .containsEntry("retryable", false)
                .containsEntry("traceId", "trace-1");
    }
}
