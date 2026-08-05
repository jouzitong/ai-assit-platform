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

/**
 * 当前用户的聊天会话管理接口。
 *
 * <p>所有查询和变更均从登录上下文取得用户标识，客户端传入的用户信息不会决定数据归属。</p>
 */
@RestController
@AllArgsConstructor
public class ConversationManageController implements IConversationManageController {

    private final ConversationService service;

    private final IApiResConvert apiResConvert;

    /**
     * 查询当前用户的会话列表。
     *
     * @param request 可选查询请求体，包含分页或筛选条件；服务端会注入当前用户
     * @return 当前用户可访问的会话摘要列表
     */
    @Override
    public List<ConversationSessionVO> list(@RequestBody(required = false) ConversationQueryRequest request) {
        ConversationQueryRequest actualRequest = request == null ? new ConversationQueryRequest() : request;
        actualRequest.setUserId(resolveCurrentUserId());
        return toVOList(service.listConversations(actualRequest));
    }

    /**
     * 按会话名称或会话编码搜索当前用户的会话。
     *
     * @param request 搜索请求体，包含关键字；关键字为空时返回全部会话
     * @return 匹配关键字的会话摘要列表
     */
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

    /**
     * 重命名当前用户拥有的会话。
     *
     * @param request 重命名请求体，包含会话编码和新的展示名称
     * @return 修改后的会话摘要
     */
    @Override
    public ConversationSessionVO rename(@RequestBody ConversationRenameRequest request) {
        request.setUserId(resolveCurrentUserId());
        return apiResConvert.toVO(service.renameConversation(request));
    }

    /**
     * 设置或取消当前用户会话的置顶状态。
     *
     * @param request 置顶请求体，包含会话编码和目标置顶状态
     * @return 更新后的会话摘要
     */
    @Override
    public ConversationSessionVO pin(@RequestBody ConversationPinRequest request) {
        request.setUserId(resolveCurrentUserId());
        return apiResConvert.toVO(service.pinConversation(request));
    }

    /**
     * 删除当前用户拥有的会话及其关联历史。
     *
     * @param request 删除请求体，包含待删除的会话定位信息
     * @return 是否成功完成删除
     */
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
