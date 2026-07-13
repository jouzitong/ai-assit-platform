package ai.platform.aiassit.conversation.workflow.support;

import ai.platform.aiassit.chat.history.entity.dto.AiChatActivityDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.service.AiChatActivityService;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowHistoryRecorderTest {

    private final AiChatMessageService messageService = mock(AiChatMessageService.class);
    private final AiChatArtifactService artifactService = mock(AiChatArtifactService.class);
    private final AiChatActivityService activityService = mock(AiChatActivityService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkflowHistoryRecorder recorder = new WorkflowHistoryRecorder(
            messageService, artifactService, activityService, objectMapper);

    @Test
    void returnsEmptyAndDoesNotPropagateWhenActivityPersistenceFails() {
        when(activityService.queryAll(any())).thenReturn(List.of());
        when(activityService.add(any())).thenThrow(new IllegalStateException("database unavailable"));

        assertThat(recorder.saveActivity(
                context(), "AI_AGENT", "RUNNING", "calling tool", "RUNNING",
                Map.of("callId", "call-1", "activityType", "TOOL_CALL")))
                .isEmpty();

        verify(activityService).add(any(AiChatActivityDTO.class));
    }

    @Test
    void returnsPersistedActivityWhenPersistenceSucceeds() {
        when(activityService.queryAll(any())).thenReturn(List.of());
        when(activityService.add(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(recorder.saveActivity(
                context(), "AI_AGENT", "COMPLETED", "tool completed", "SUCCESS",
                Map.of("callId", "call-1", "activityType", "TOOL_CALL")))
                .get()
                .satisfies(activity -> {
                    assertThat(activity.getSessionCode()).isEqualTo("session-1");
                    assertThat(activity.getRoundCode()).isEqualTo("round-1");
                    assertThat(activity.getCorrelationCode()).isEqualTo("call-1");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistsSafeFailureMessageLinkedToCurrentUserMessage() throws Exception {
        when(messageService.add(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String rawError = "invalid API key Authorization: Bearer sk-liveSecret123 token=raw-token "
                + "x".repeat(700);

        assertThat(recorder.saveFailureMessage(context(), rawError)).isPresent();

        ArgumentCaptor<AiChatMessageDTO> message = ArgumentCaptor.forClass(AiChatMessageDTO.class);
        verify(messageService).add(message.capture());
        AiChatMessageDTO saved = message.getValue();
        assertThat(saved.getRole()).isEqualTo("ASSISTANT");
        assertThat(saved.getActorType()).isEqualTo("AI");
        assertThat(saved.getMessageType()).isEqualTo("ERROR_MESSAGE");
        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(saved.getContent()).isEmpty();
        assertThat(saved.getParentMessageCode()).isEqualTo("user-message-1");
        assertThat(saved.getSourceMessageCode()).isEqualTo("user-message-1");

        Map<String, Object> ext = objectMapper.readValue(saved.getExtJson(), Map.class);
        Map<String, Object> error = (Map<String, Object>) ext.get("error");
        assertThat(error)
                .containsEntry("code", "MODEL_CREDENTIAL_INVALID")
                .containsEntry("userMessage", "模型服务凭证无效，请联系管理员检查配置")
                .containsEntry("retryable", false)
                .containsEntry("traceId", "trace-1");
        assertThat((String) error.get("detail"))
                .hasSize(500)
                .endsWith("…")
                .contains("Authorization: ***", "token=***")
                .doesNotContain("sk-liveSecret123", "raw-token");
        assertThat(saved.getExtJson()).doesNotContain("sk-liveSecret123", "raw-token");
    }

    @Test
    void doesNotPropagateFailureMessagePersistenceError() {
        when(messageService.add(any())).thenThrow(new IllegalStateException("database unavailable"));

        assertThat(recorder.saveFailureMessage(context(), "python process timeout")).isEmpty();
    }

    @Test
    void reusesExistingFailureMessageForCurrentRound() {
        ConversationRuntimeContext context = context();
        AiChatMessageDTO existing = new AiChatMessageDTO();
        existing.setRoundCode("round-1");
        existing.setMessageType("ERROR_MESSAGE");
        existing.setStatus("FAILED");
        context.getOrCreateUserMessageContext().setSessionMessages(List.of(existing));

        assertThat(recorder.saveFailureMessage(context, "workflow execution failed"))
                .contains(existing);

        verify(messageService, never()).add(any());
    }

    private ConversationRuntimeContext context() {
        AiChatSessionDTO session = new AiChatSessionDTO();
        session.setSessionCode("session-1");
        session.setUserId(7L);
        AiChatRoundDTO round = new AiChatRoundDTO();
        round.setRoundCode("round-1");
        AiChatMessageDTO userMessage = new AiChatMessageDTO();
        userMessage.setMessageCode("user-message-1");
        userMessage.setRoundCode("round-1");
        userMessage.setRole("USER");
        ConversationQueryCommand command = new ConversationQueryCommand();
        command.setTraceId("trace-1");
        ConversationRuntimeContext context = new ConversationRuntimeContext();
        context.setSession(session);
        context.setRound(round);
        context.setCommand(command);
        context.getOrCreateUserMessageContext().setCurrentMessage(userMessage);
        context.getOrCreateUserMessageContext().setSessionMessages(List.of(userMessage));
        return context;
    }
}
