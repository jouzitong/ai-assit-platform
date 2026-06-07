package ai.platform.aiassit.chat.core.query.controller.impl;

import ai.platform.aiassit.chat.core.query.AiChatQueryService;
import ai.platform.aiassit.chat.core.query.controller.AiChatQueryApi;
import ai.platform.aiassit.chat.core.query.convert.IApiResConvert;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationDetailResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationPinRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationRenameRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatSessionVO;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationCreateRequest;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationDeleteRequest;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationDetailRequest;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/chat")
@AllArgsConstructor
public class AiChatQueryController implements AiChatQueryApi {

    private final AiChatQueryService service;

    private final IApiResConvert apiResConvert;

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
    public List<AiChatSessionVO> list(@RequestBody(required = false) AiChatConversationQueryRequest request) {
        List<AiChatSessionDTO> aiChatSessionDTOS = service.listConversations(request);
        return aiChatSessionDTOS.stream().map(apiResConvert::toVO).toList();
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
    public AiChatSessionVO renameConversation(@RequestBody AiChatConversationRenameRequest request) {
        return apiResConvert.toVO(service.renameConversation(request));
    }

    @Override
    @PostMapping("/conversation/pin")
    public AiChatSessionVO pinConversation(@RequestBody AiChatConversationPinRequest request) {
        return apiResConvert.toVO(service.pinConversation(request));
    }

    @Override
    @PostMapping("/conversation/delete")
    public Boolean deleteConversation(@RequestBody AiChatConversationDeleteRequest request) {
        return service.deleteConversation(request);
    }
}
