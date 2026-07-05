package ai.platform.aiassit.conversation.conversation.controller.impl;

import ai.platform.aiassit.conversation.conversation.controller.AiChatConversationApi;
import ai.platform.aiassit.conversation.conversation.service.AiChatConversationService;
import ai.platform.aiassit.conversation.query.convert.IApiResConvert;
import ai.platform.aiassit.conversation.query.dto.AiChatConversationDetailResponse;
import ai.platform.aiassit.conversation.query.dto.AiChatConversationPinRequest;
import ai.platform.aiassit.conversation.query.dto.AiChatConversationQueryRequest;
import ai.platform.aiassit.conversation.query.dto.AiChatConversationRenameRequest;
import ai.platform.aiassit.conversation.query.dto.AiChatSessionVO;
import ai.platform.aiassit.conversation.query.req.AiChatConversationCreateRequest;
import ai.platform.aiassit.conversation.query.req.AiChatConversationDeleteRequest;
import ai.platform.aiassit.conversation.query.req.AiChatConversationDetailRequest;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import lombok.AllArgsConstructor;
import org.arthena.framework.common.context.SystemContext;
import org.athena.framework.security.api.model.UserContext;
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
        if (request == null) {
            request = new AiChatConversationQueryRequest();
        }
        request.setUserId(resolveCurrentUserId());
        List<AiChatSessionDTO> aiChatSessionDTOS = service.listConversations(request);
        return aiChatSessionDTOS.stream().map(apiResConvert::toVO).toList();
    }

    @Override
    @PostMapping("/conversation/detail")
    public AiChatConversationDetailResponse detail(@RequestBody AiChatConversationDetailRequest request) {
        request.setUserId(resolveCurrentUserId());
        return service.detailConversation(request);
    }

    @Override
    @PostMapping("/conversation/create")
    public AiChatConversationDetailResponse create(@RequestBody(required = false) AiChatConversationCreateRequest request) {
        if (request == null) {
            request = new AiChatConversationCreateRequest();
        }
        request.setUserId(resolveCurrentUserId());
        return service.createConversation(request);
    }

    @Override
    @PostMapping("/conversation/rename")
    public AiChatSessionVO renameConversation(@RequestBody AiChatConversationRenameRequest request) {
        request.setUserId(resolveCurrentUserId());
        return apiResConvert.toVO(service.renameConversation(request));
    }

    @Override
    @PostMapping("/conversation/pin")
    public AiChatSessionVO pinConversation(@RequestBody AiChatConversationPinRequest request) {
        request.setUserId(resolveCurrentUserId());
        return apiResConvert.toVO(service.pinConversation(request));
    }

    @Override
    @PostMapping("/conversation/delete")
    public Boolean deleteConversation(@RequestBody AiChatConversationDeleteRequest request) {
        request.setUserId(resolveCurrentUserId());
        return service.deleteConversation(request);
    }

    private Long resolveCurrentUserId() {
        UserContext userContext = SystemContext.getUserContext();
        if (userContext != null && userContext.subject() != null) {
            return userContext.subject().userId();
        }
        throw new IllegalArgumentException("current user is required");
    }
}
