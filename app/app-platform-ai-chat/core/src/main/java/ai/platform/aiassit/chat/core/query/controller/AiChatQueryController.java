package ai.platform.aiassit.chat.core.query.controller;

import ai.platform.aiassit.chat.api.AiChatQueryApi;
import ai.platform.aiassit.chat.api.dto.AiChatConversationCreateRequest;
import ai.platform.aiassit.chat.api.dto.AiChatConversationDetailRequest;
import ai.platform.aiassit.chat.api.dto.AiChatConversationDetailResponse;
import ai.platform.aiassit.chat.api.dto.AiChatConversationDeleteRequest;
import ai.platform.aiassit.chat.api.dto.AiChatConversationPinRequest;
import ai.platform.aiassit.chat.api.dto.AiChatConversationQueryRequest;
import ai.platform.aiassit.chat.api.dto.AiChatConversationRenameRequest;
import ai.platform.aiassit.chat.api.dto.AiChatQueryRequest;
import ai.platform.aiassit.chat.api.dto.AiChatQueryResponse;
import ai.platform.aiassit.chat.core.query.AiChatQueryService;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/chat")
public class AiChatQueryController implements AiChatQueryApi {

    private final AiChatQueryService service;

    public AiChatQueryController(AiChatQueryService service) {
        this.service = service;
    }

    @Override
    @PostMapping("/query")
    public AiChatQueryResponse query(@RequestBody AiChatQueryRequest request) {
        return service.query(request);
    }

    @Override
    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryStream(@RequestBody AiChatQueryRequest request) {
        return service.queryStream(request);
    }

    @Override
    @PostMapping("/conversation/list")
    public List<AiChatSessionDTO> list(@RequestBody(required = false) AiChatConversationQueryRequest request) {
        return service.listConversations(request);
    }

    @Override
    @PostMapping("/conversation/detail")
    public AiChatConversationDetailResponse detail(@RequestBody AiChatConversationDetailRequest request) {
        return service.detailConversation(request);
    }

    @Override
    @PostMapping("/conversation/create")
    public AiChatConversationDetailResponse create(@RequestBody(required = false) AiChatConversationCreateRequest request) {
        return service.createConversation(request);
    }

    @Override
    @PostMapping("/conversation/rename")
    public ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO renameConversation(@RequestBody AiChatConversationRenameRequest request) {
        return service.renameConversation(request);
    }

    @Override
    @PostMapping("/conversation/pin")
    public ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO pinConversation(@RequestBody AiChatConversationPinRequest request) {
        return service.pinConversation(request);
    }

    @Override
    @PostMapping("/conversation/delete")
    public Boolean deleteConversation(@RequestBody AiChatConversationDeleteRequest request) {
        return service.deleteConversation(request);
    }
}
