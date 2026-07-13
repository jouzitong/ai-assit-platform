package ai.platform.aiassit.conversation.workflow.engine.impl;

import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.workflow.engine.transition.TransitionResolver;
import ai.platform.aiassit.conversation.workflow.support.WorkflowHistoryRecorder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultWorkflowEngineImplTest {

    @Test
    void attemptsFailureMessagePersistenceOnlyOnceWhenFailureHandlingReenters() {
        AiChatRoundService roundService = mock(AiChatRoundService.class);
        WorkflowHistoryRecorder historyRecorder = mock(WorkflowHistoryRecorder.class);
        when(historyRecorder.saveFailureMessage(any(), any())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("round update failed"))
                .when(roundService).edit(eq(1L), any(AiChatRoundDTO.class));

        DefaultWorkflowEngineImpl engine = new DefaultWorkflowEngineImpl(
                List.of(), roundService, mock(TransitionResolver.class), historyRecorder);
        ConversationRuntimeContext context = new ConversationRuntimeContext();
        AiChatRoundDTO round = new AiChatRoundDTO();
        round.setId(1L);
        round.setRoundCode("round-1");
        context.setRound(round);

        assertThatThrownBy(() -> engine.run(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("round update failed");

        verify(historyRecorder, times(1))
                .saveFailureMessage(context, "workflow definition is required");
    }
}
