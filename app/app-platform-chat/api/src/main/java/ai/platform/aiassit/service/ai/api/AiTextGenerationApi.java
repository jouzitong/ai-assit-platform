package ai.platform.aiassit.service.ai.api;

import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationRequest;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationResponse;
import org.athena.framework.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** 内部通用文本生成 API。 */
@FeignClient(
        name = "chat",
        contextId = "platformChatTextGenerationClient",
        path = "/chat"
)
public interface AiTextGenerationApi {

    @PostMapping("/internal/v1/ai/text/generate")
    R<AiTextGenerationResponse> generate(@RequestBody AiTextGenerationRequest request);
}
