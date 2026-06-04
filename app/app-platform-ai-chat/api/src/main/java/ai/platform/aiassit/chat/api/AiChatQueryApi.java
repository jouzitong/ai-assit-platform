package ai.platform.aiassit.chat.api;

import ai.platform.aiassit.chat.api.dto.AiChatConversationCreateRequest;
import ai.platform.aiassit.chat.api.dto.AiChatConversationDetailRequest;
import ai.platform.aiassit.chat.api.dto.AiChatConversationDetailResponse;
import ai.platform.aiassit.chat.api.dto.AiChatConversationDeleteRequest;
import ai.platform.aiassit.chat.api.dto.AiChatConversationPinRequest;
import ai.platform.aiassit.chat.api.dto.AiChatConversationQueryRequest;
import ai.platform.aiassit.chat.api.dto.AiChatConversationRenameRequest;
import ai.platform.aiassit.chat.api.dto.AiChatQueryRequest;
import ai.platform.aiassit.chat.api.dto.AiChatQueryResponse;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@FeignClient(
        name = "${app.platform-ai-chat.name:app-platform-ai-chat}",
        url = "${app.platform-ai-chat.url:http://127.0.0.1:13102/aiChat}"
)
public interface AiChatQueryApi {

    @PostMapping("/api/v1/ai/chat/query")
    AiChatQueryResponse query(@RequestBody AiChatQueryRequest request);

    @PostMapping(value = "/api/v1/ai/chat/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter queryStream(@RequestBody AiChatQueryRequest request);

    @PostMapping("/api/v1/ai/chat/conversation/list")
    List<AiChatSessionDTO> list(@RequestBody(required = false) AiChatConversationQueryRequest request);

    @PostMapping("/api/v1/ai/chat/conversation/detail")
    AiChatConversationDetailResponse detail(@RequestBody AiChatConversationDetailRequest request);

    @PostMapping("/api/v1/ai/chat/conversation/create")
    AiChatConversationDetailResponse create(@RequestBody(required = false) AiChatConversationCreateRequest request);

    @PostMapping("/api/v1/ai/chat/conversation/rename")
    AiChatSessionDTO renameConversation(@RequestBody AiChatConversationRenameRequest request);

    @PostMapping("/api/v1/ai/chat/conversation/pin")
    AiChatSessionDTO pinConversation(@RequestBody AiChatConversationPinRequest request);

    @PostMapping("/api/v1/ai/chat/conversation/delete")
    Boolean deleteConversation(@RequestBody AiChatConversationDeleteRequest request);
}
