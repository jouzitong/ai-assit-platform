package ai.platform.aiassist.service.ai.api;

import ai.platform.aiassist.service.ai.api.dto.AiMetaQueryRequest;
import ai.platform.aiassist.service.ai.api.dto.AiModelConfigDTO;
import ai.platform.aiassist.service.ai.api.dto.AiModelCredentialDTO;
import ai.platform.aiassist.service.ai.api.dto.AiProviderConfigDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "${spring.application.name}",
        contextId = "platformAiEngineClient",
        path = "/aiEngine"
)
public interface AiMetaQueryApi {

    @PostMapping("/provider/list")
    List<AiProviderConfigDTO> listProviders(@RequestBody(required = false) AiMetaQueryRequest request);

    @PostMapping("/model/list")
    List<AiModelConfigDTO> listModels(@RequestBody(required = false) AiMetaQueryRequest request);

    @PostMapping("/credential/list")
    List<AiModelCredentialDTO> listCredentials(@RequestBody(required = false) AiMetaQueryRequest request);
}
