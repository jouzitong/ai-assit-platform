package ai.platform.aiassit.db.engine.virtualization.adapter.external;

import ai.platform.aiassit.data.virtualization.spi.text.TextGenerationCommand;
import ai.platform.aiassit.data.virtualization.spi.text.TextGenerationResult;
import ai.platform.aiassit.service.ai.api.AiTextGenerationApi;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationRequest;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationResponse;
import ai.platform.aiassit.user.system.settings.api.SystemSettingInternalApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.athena.framework.web.vo.R;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTextGenerationAdapterTest {

    private final AiTextGenerationApi textGenerationApi = mock(AiTextGenerationApi.class);
    private final SystemSettingInternalApi systemSettingInternalApi = mock(SystemSettingInternalApi.class);
    private final AiTextGenerationAdapter adapter = new AiTextGenerationAdapter(
            textGenerationApi, systemSettingInternalApi, new ObjectMapper());

    @Test
    void loadsModelAndGenerationOptionsFromSystemSetting() {
        when(systemSettingInternalApi.queryValueByKey(AiTextGenerationAdapter.DEFAULT_AI_MODEL_SETTING_KEY))
                .thenReturn(R.ok("""
                        {"modelCode":"openai.gpt-4.1-mini","maxTokens":1600,"temperature":0.15}
                        """));
        when(textGenerationApi.generate(any())).thenReturn(R.ok(
                new AiTextGenerationResponse("generated", "gpt-4.1-mini", "request-1")));

        TextGenerationResult result = adapter.generate(new TextGenerationCommand(
                "system", "user", "virtual-table-test"));

        assertThat(result.text()).isEqualTo("generated");
        ArgumentCaptor<AiTextGenerationRequest> requestCaptor =
                ArgumentCaptor.forClass(AiTextGenerationRequest.class);
        verify(textGenerationApi).generate(requestCaptor.capture());
        AiTextGenerationRequest request = requestCaptor.getValue();
        assertThat(request.getModelCode()).isEqualTo("openai.gpt-4.1-mini");
        assertThat(request.getMaxTokens()).isEqualTo(1600);
        assertThat(request.getTemperature()).isEqualTo(0.15D);
        assertThat(request.getSystemPrompt()).isEqualTo("system");
        assertThat(request.getUserPrompt()).isEqualTo("user");
        assertThat(request.getScene()).isEqualTo("virtual-table-test");
    }

    @Test
    void rejectsInvalidSystemSettingBeforeCallingChat() {
        when(systemSettingInternalApi.queryValueByKey(AiTextGenerationAdapter.DEFAULT_AI_MODEL_SETTING_KEY))
                .thenReturn(R.ok("{\"modelCode\":\"openai.gpt-4.1-mini\",\"temperature\":0.2}"));

        assertThatThrownBy(() -> adapter.generate(new TextGenerationCommand("system", "user", "test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AiTextGenerationAdapter.DEFAULT_AI_MODEL_SETTING_KEY);
        verify(textGenerationApi, never()).generate(any());
    }
}
