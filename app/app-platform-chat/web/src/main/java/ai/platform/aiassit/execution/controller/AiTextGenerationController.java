package ai.platform.aiassit.execution.controller;

import ai.platform.aiassit.execution.service.AiTextGenerationService;
import ai.platform.aiassit.service.ai.api.AiTextGenerationApi;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationRequest;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationResponse;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiTextGenerationController implements AiTextGenerationApi {

    private final AiTextGenerationService generationService;

    public AiTextGenerationController(AiTextGenerationService generationService) {
        this.generationService = generationService;
    }

    @Override
    public R<AiTextGenerationResponse> generate(@RequestBody AiTextGenerationRequest request) {
        return R.ok(generationService.generate(request));
    }
}
