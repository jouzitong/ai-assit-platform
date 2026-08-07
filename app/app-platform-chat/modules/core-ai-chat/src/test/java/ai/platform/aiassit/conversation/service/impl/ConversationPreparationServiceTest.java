package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
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
                session(ConversationBusinessType.PAGE_ASSISTANT), command(ConversationBusinessType.PAGE_ASSISTANT)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> service.validateSessionBusinessType(
                session(ConversationBusinessType.CUSTOM), command(ConversationBusinessType.PAGE_ASSISTANT)))
                .isInstanceOf(BizException.class);
    }

    @Test
    void homeChatAcceptsLegacySessionTypesButRejectsPageAssistantSession() {
        assertThatCode(() -> service.validateSessionBusinessType(
                session(ConversationBusinessType.CUSTOM), command(ConversationBusinessType.CUSTOM)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validateSessionBusinessType(
                session(ConversationBusinessType.GENERAL), command(ConversationBusinessType.CUSTOM)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validateSessionBusinessType(
                session(null), command(ConversationBusinessType.CUSTOM)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> service.validateSessionBusinessType(
                session(ConversationBusinessType.PAGE_ASSISTANT), command(ConversationBusinessType.CUSTOM)))
                .isInstanceOf(BizException.class);
    }

    private ConversationSessionDTO session(ConversationBusinessType businessType) {
        ConversationSessionDTO session = new ConversationSessionDTO();
        session.setSessionCode("session-1");
        session.setBusinessType(businessType);
        return session;
    }

    private ConversationQueryCommand command(ConversationBusinessType businessType) {
        ConversationQueryCommand command = new ConversationQueryCommand();
        command.setBusinessType(businessType);
        return command;
    }
}
