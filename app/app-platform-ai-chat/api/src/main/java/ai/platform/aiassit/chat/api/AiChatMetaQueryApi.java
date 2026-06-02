package ai.platform.aiassit.chat.api;

import ai.platform.aiassit.chat.api.dto.AiChatMetaQueryRequest;
import ai.platform.aiassit.chat.api.dto.AiModelConfigDto;
import ai.platform.aiassit.chat.api.dto.AiModelCredentialDto;
import ai.platform.aiassit.chat.api.dto.AiProviderConfigDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@FeignClient(
        name = "${app.platform-ai-chat.name:app-platform-ai-chat}",
        url = "${app.platform-ai-chat.url:http://127.0.0.1:13102}"
)
@RequestMapping("/api/v1/ai/chat/meta")
public interface AiChatMetaQueryApi {

    @PostMapping("/provider/list")
    List<AiProviderConfigDto> listProviders(@RequestBody(required = false) AiChatMetaQueryRequest request);

    @PostMapping("/model/list")
    List<AiModelConfigDto> listModels(@RequestBody(required = false) AiChatMetaQueryRequest request);

    @PostMapping("/credential/list")
    List<AiModelCredentialDto> listCredentials(@RequestBody(required = false) AiChatMetaQueryRequest request);
}
