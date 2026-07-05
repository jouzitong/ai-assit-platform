package ai.platform.aiassit.chat.core.query.controller.impl;

import ai.platform.aiassit.chat.core.conversation.service.AiChatConversationService;
import ai.platform.aiassit.chat.core.query.controller.AiChatConversationManageApi;
import ai.platform.aiassit.chat.core.query.convert.IApiResConvert;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationPinRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationRenameRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationSearchRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatSessionVO;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationDeleteRequest;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import lombok.AllArgsConstructor;
import org.arthena.framework.common.context.SystemContext;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@AllArgsConstructor
public class AiChatConversationManageController implements AiChatConversationManageApi {

    private final AiChatConversationService service;

    private final IApiResConvert apiResConvert;

    @Override
    public List<AiChatSessionVO> list(@RequestBody(required = false) AiChatConversationQueryRequest request) {
        AiChatConversationQueryRequest actualRequest = request == null ? new AiChatConversationQueryRequest() : request;
        actualRequest.setUserId(resolveCurrentUserId());
        return toVOList(service.listConversations(actualRequest));
    }

    @Override
    public List<AiChatSessionVO> search(@RequestBody AiChatConversationSearchRequest request) {
        AiChatConversationSearchRequest actualRequest = request == null ? new AiChatConversationSearchRequest() : request;
        AiChatConversationQueryRequest queryRequest = new AiChatConversationQueryRequest();
        queryRequest.setUserId(resolveCurrentUserId());
        String keyword = normalizeKeyword(actualRequest.getKeyword());
        return toVOList(service.listConversations(queryRequest)).stream()
                .filter(session -> matchesKeyword(session, keyword))
                .toList();
    }

    @Override
    public AiChatSessionVO rename(@RequestBody AiChatConversationRenameRequest request) {
        request.setUserId(resolveCurrentUserId());
        return apiResConvert.toVO(service.renameConversation(request));
    }

    @Override
    public AiChatSessionVO pin(@RequestBody AiChatConversationPinRequest request) {
        request.setUserId(resolveCurrentUserId());
        return apiResConvert.toVO(service.pinConversation(request));
    }

    @Override
    public Boolean delete(@RequestBody AiChatConversationDeleteRequest request) {
        request.setUserId(resolveCurrentUserId());
        return service.deleteConversation(request);
    }

    private List<AiChatSessionVO> toVOList(List<AiChatSessionDTO> sessions) {
        return sessions.stream().map(apiResConvert::toVO).toList();
    }

    private boolean matchesKeyword(AiChatSessionVO session, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return containsIgnoreCase(session.getSessionName(), keyword)
                || containsIgnoreCase(session.getSessionCode(), keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return StringUtils.hasText(value)
                && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private Long resolveCurrentUserId() {
        UserContext userContext = SystemContext.getUserContext();
        if (userContext != null && userContext.subject() != null) {
            return userContext.subject().userId();
        }
        throw new IllegalArgumentException("current user is required");
    }
}
