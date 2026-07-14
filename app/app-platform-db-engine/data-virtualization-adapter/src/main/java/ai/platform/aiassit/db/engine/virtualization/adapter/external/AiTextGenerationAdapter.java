package ai.platform.aiassit.db.engine.virtualization.adapter.external;

import ai.platform.aiassit.data.virtualization.spi.text.TextGenerationCommand;
import ai.platform.aiassit.data.virtualization.spi.text.TextGenerationPort;
import ai.platform.aiassit.data.virtualization.spi.text.TextGenerationResult;
import ai.platform.aiassit.service.ai.api.AiTextGenerationApi;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationRequest;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationResponse;
import org.athena.framework.web.vo.R;
import org.springframework.stereotype.Component;

/** Keeps Chat text-generation HTTP DTOs outside the virtualization core. */
@Component
public class AiTextGenerationAdapter implements TextGenerationPort {

    private final AiTextGenerationApi textGenerationApi;

    public AiTextGenerationAdapter(AiTextGenerationApi textGenerationApi) {
        this.textGenerationApi = textGenerationApi;
    }

    @Override
    public TextGenerationResult generate(TextGenerationCommand command) {
        if (command == null || command.userPrompt() == null || command.userPrompt().isBlank()) {
            throw new IllegalArgumentException("text generation command/userPrompt 不能为空");
        }
        AiTextGenerationRequest request = new AiTextGenerationRequest();
        request.setSystemPrompt(command.systemPrompt());
        request.setUserPrompt(command.userPrompt());
        request.setScene(command.scene());
        request.setMaxTokens(command.maxTokens());
        request.setTemperature(command.temperature());
        R<AiTextGenerationResponse> response = textGenerationApi.generate(request);
        if (response == null) {
            throw new IllegalStateException("文本生成失败: 无响应");
        }
        if (!response.isOk()) {
            throw new IllegalStateException("文本生成失败, code=" + response.getCode());
        }
        if (response.getData() == null || response.getData().getText() == null
                || response.getData().getText().isBlank()) {
            throw new IllegalStateException("文本生成失败: 响应文本为空");
        }
        return new TextGenerationResult(response.getData().getText());
    }
}
