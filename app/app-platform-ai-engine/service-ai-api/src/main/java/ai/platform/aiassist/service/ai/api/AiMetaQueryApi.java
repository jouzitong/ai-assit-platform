package ai.platform.aiassist.service.ai.api;

import ai.platform.aiassist.service.ai.api.dto.AiMetaQueryRequest;
import ai.platform.aiassist.service.ai.api.dto.AiModelConfigDTO;
import ai.platform.aiassist.service.ai.api.dto.AiModelCredentialDTO;
import ai.platform.aiassist.service.ai.api.dto.AiProviderConfigDTO;
import ai.platform.aiassist.service.ai.api.dto.AiProviderModelOverviewDTO;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "app-platform-ai-engine",
        contextId = "platformAiEngineClient",
        path = "/aiEngine"
)
public interface AiMetaQueryApi {

    @PostMapping("/internal/v1/ai/meta/provider-model/overview")
    AiProviderModelOverviewDTO providerModelOverview(@RequestBody(required = false) AiMetaQueryRequest request);

    @PostMapping("/internal/v1/ai/meta/provider/list")
    List<AiProviderConfigDTO> listProviders(@RequestBody(required = false) AiMetaQueryRequest request);

    @PostMapping(value = "/internal/v1/ai/meta/model/list",produces = MediaType.APPLICATION_JSON_VALUE)
    @IgnoredResultWrapper
    List<AiModelConfigDTO> listModels(@RequestBody(required = false) AiMetaQueryRequest request);

    @PostMapping("/internal/v1/ai/meta/credential/list")
    List<AiModelCredentialDTO> listCredentials(@RequestBody(required = false) AiMetaQueryRequest request);
}
