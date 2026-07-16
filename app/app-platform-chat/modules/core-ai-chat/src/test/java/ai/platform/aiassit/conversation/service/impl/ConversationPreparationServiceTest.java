package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import org.arthena.framework.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationPreparationServiceTest {

    private final ConversationPreparationService service = new ConversationPreparationService(
            null, null, null, null, null);

    @Test
    void pageAssistantOnlyContinuesPageAssistantSession() {
        assertThatCode(() -> service.validateSessionBusinessType(
                session(AiChatBusinessType.PAGE_ASSISTANT), command(AiChatBusinessType.PAGE_ASSISTANT)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> service.validateSessionBusinessType(
                session(AiChatBusinessType.CUSTOM), command(AiChatBusinessType.PAGE_ASSISTANT)))
                .isInstanceOf(BizException.class);
    }

    @Test
    void homeChatAcceptsLegacySessionTypesButRejectsPageAssistantSession() {
        assertThatCode(() -> service.validateSessionBusinessType(
                session(AiChatBusinessType.CUSTOM), command(AiChatBusinessType.CUSTOM)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validateSessionBusinessType(
                session(AiChatBusinessType.GENERAL), command(AiChatBusinessType.CUSTOM)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validateSessionBusinessType(
                session(null), command(AiChatBusinessType.CUSTOM)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> service.validateSessionBusinessType(
                session(AiChatBusinessType.PAGE_ASSISTANT), command(AiChatBusinessType.CUSTOM)))
                .isInstanceOf(BizException.class);
    }

    private AiChatSessionDTO session(AiChatBusinessType businessType) {
        AiChatSessionDTO session = new AiChatSessionDTO();
        session.setSessionCode("session-1");
        session.setBusinessType(businessType);
        return session;
    }

    private ConversationQueryCommand command(AiChatBusinessType businessType) {
        ConversationQueryCommand command = new ConversationQueryCommand();
        command.setBusinessType(businessType);
        return command;
    }
}
