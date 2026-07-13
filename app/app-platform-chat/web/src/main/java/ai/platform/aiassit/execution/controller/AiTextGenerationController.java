package ai.platform.aiassit.execution.controller;

import ai.platform.aiassit.execution.service.AiTextGenerationService;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationRequest;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/ai/text")
public class AiTextGenerationController {

    private final AiTextGenerationService generationService;

    public AiTextGenerationController(AiTextGenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping("/generate")
    public AiTextGenerationResponse generate(@RequestBody AiTextGenerationRequest request) {
        return generationService.generate(request);
    }
}
