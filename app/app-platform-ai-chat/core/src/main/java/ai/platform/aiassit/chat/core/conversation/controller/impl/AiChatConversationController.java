package ai.platform.aiassit.chat.core.conversation.controller.impl;

import ai.platform.aiassit.chat.core.conversation.AiChatConversationService;
import ai.platform.aiassit.chat.core.conversation.controller.AiChatConversationApi;
import ai.platform.aiassit.chat.core.query.convert.IApiResConvert;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationDetailResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationPinRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationRenameRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatSessionVO;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationCreateRequest;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationDeleteRequest;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationDetailRequest;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/chat")
@AllArgsConstructor
public class AiChatConversationController implements AiChatConversationApi {

    private final AiChatConversationService service;

    private final IApiResConvert apiResConvert;

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
