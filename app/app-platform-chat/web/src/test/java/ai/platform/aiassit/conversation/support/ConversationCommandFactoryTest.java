package ai.platform.aiassit.conversation.support;

import ai.platform.aiassit.conversation.dto.chat.ConversationQueryRequest;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.model.service.AiModelConfigService;
import org.arthena.framework.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationCommandFactoryTest {

    private final AiModelConfigService modelConfigService = mock(AiModelConfigService.class);
    private final ConversationCommandFactory factory = new ConversationCommandFactory(modelConfigService);

    @Test
    void resolvesFrontendModelIdToInternalCodeAndActualModel() {
        AiModelConfigDTO config = new AiModelConfigDTO();
        config.setId(42L);
        config.setModelCode("qwen-primary");
        config.setApiModel("qwen-plus-2026-07");
        config.setEnabled(true);
        when(modelConfigService.getResolvedById(42L)).thenReturn(config);
        ConversationQueryRequest request = new ConversationQueryRequest();
        request.setModelId(42L);
        request.setMessage("hello");

        ConversationQueryCommand command = factory.fromLegacy(request, 7L, "trace-1");

        assertThat(command.getModelId()).isEqualTo(42L);
        assertThat(command.getApiModel()).isEqualTo("qwen-primary");
        assertThat(command.getActualModel()).isEqualTo("qwen-plus-2026-07");
    }

    @Test
    void rejectsDisabledOrMissingModelId() {
        when(modelConfigService.getResolvedById(99L)).thenReturn(null);
        ConversationQueryRequest request = new ConversationQueryRequest();
        request.setModelId(99L);
        request.setMessage("hello");

        assertThatThrownBy(() -> factory.fromLegacy(request, 7L, "trace-1"))
                .isInstanceOf(BizException.class);
    }
}
