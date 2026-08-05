package ai.platform.aiassit.execution.controller;

import ai.platform.aiassit.execution.service.AiTextGenerationService;
import ai.platform.aiassit.service.ai.api.AiTextGenerationApi;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationRequest;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationResponse;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部通用文本生成接口。
 *
 * <p>为平台内其他服务提供统一的文本生成入口，具体模型选择、调用与结果归一化由文本生成服务负责。</p>
 */
@RestController
public class AiTextGenerationController implements AiTextGenerationApi {

    private final AiTextGenerationService generationService;

    public AiTextGenerationController(AiTextGenerationService generationService) {
        this.generationService = generationService;
    }

    /**
     * 根据提示词和模型调用参数生成文本。
     *
     * @param request 文本生成请求体，包含模型、消息或提示词及生成参数
     * @return 包装后的文本生成结果，包含生成内容和模型调用信息
     */
    @Override
    public R<AiTextGenerationResponse> generate(@RequestBody AiTextGenerationRequest request) {
        return R.ok(generationService.generate(request));
    }
}
