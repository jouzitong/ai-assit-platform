package ai.platform.aiassit.conversation.controller.impl;

import ai.platform.aiassit.conversation.service.ConversationService;
import ai.platform.aiassit.conversation.controller.IConversationManageController;
import ai.platform.aiassit.conversation.convert.IApiResConvert;
import ai.platform.aiassit.conversation.dto.conversation.ConversationPinRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationQueryRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationRenameRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationSearchRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationSessionVO;
import ai.platform.aiassit.conversation.dto.conversation.ConversationDeleteRequest;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import lombok.AllArgsConstructor;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@AllArgsConstructor
public class ConversationManageController implements IConversationManageController {

    private final ConversationService service;

    private final IApiResConvert apiResConvert;

    @Override
    public List<ConversationSessionVO> list(@RequestBody(required = false) ConversationQueryRequest request) {
        ConversationQueryRequest actualRequest = request == null ? new ConversationQueryRequest() : request;
        actualRequest.setUserId(resolveCurrentUserId());
        return toVOList(service.listConversations(actualRequest));
    }

    @Override
    public List<ConversationSessionVO> search(@RequestBody ConversationSearchRequest request) {
        ConversationSearchRequest actualRequest = request == null ? new ConversationSearchRequest() : request;
        ConversationQueryRequest queryRequest = new ConversationQueryRequest();
        queryRequest.setUserId(resolveCurrentUserId());
        String keyword = normalizeKeyword(actualRequest.getKeyword());
        return toVOList(service.listConversations(queryRequest)).stream()
                .filter(session -> matchesKeyword(session, keyword))
                .toList();
    }

    @Override
    public ConversationSessionVO rename(@RequestBody ConversationRenameRequest request) {
        request.setUserId(resolveCurrentUserId());
        return apiResConvert.toVO(service.renameConversation(request));
    }

    @Override
    public ConversationSessionVO pin(@RequestBody ConversationPinRequest request) {
        request.setUserId(resolveCurrentUserId());
        return apiResConvert.toVO(service.pinConversation(request));
    }

    @Override
    public Boolean delete(@RequestBody ConversationDeleteRequest request) {
        request.setUserId(resolveCurrentUserId());
        return service.deleteConversation(request);
    }

    private List<ConversationSessionVO> toVOList(List<ConversationSessionDTO> sessions) {
        return sessions.stream().map(apiResConvert::toVO).toList();
    }

    private boolean matchesKeyword(ConversationSessionVO session, String keyword) {
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
        throw BizException.of(ErrCodeConstant.UNAUTHORIZED);
    }
}
