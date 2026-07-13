package ai.platform.aiassit.conversation.workflow.support;

import ai.platform.aiassit.chat.history.entity.dto.AiChatActivityDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.service.AiChatActivityService;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowHistoryRecorderTest {

    private final AiChatMessageService messageService = mock(AiChatMessageService.class);
    private final AiChatArtifactService artifactService = mock(AiChatArtifactService.class);
    private final AiChatActivityService activityService = mock(AiChatActivityService.class);
    private final WorkflowHistoryRecorder recorder = new WorkflowHistoryRecorder(
            messageService, artifactService, activityService, new ObjectMapper());

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

    private ConversationRuntimeContext context() {
        AiChatSessionDTO session = new AiChatSessionDTO();
        session.setSessionCode("session-1");
        session.setUserId(7L);
        AiChatRoundDTO round = new AiChatRoundDTO();
        round.setRoundCode("round-1");
        ConversationRuntimeContext context = new ConversationRuntimeContext();
        context.setSession(session);
        context.setRound(round);
        return context;
    }
}
