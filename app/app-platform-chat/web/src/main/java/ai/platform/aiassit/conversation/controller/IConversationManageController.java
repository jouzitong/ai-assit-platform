package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.dto.conversation.ConversationPinRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationQueryRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationRenameRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationSearchRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationSessionVO;
import ai.platform.aiassit.conversation.dto.conversation.ConversationDeleteRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupAssignRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupCreateRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupDeleteRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupRenameRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 当前登录用户的 AI 会话管理协议契约。
 *
 * <p>会话列表、搜索与变更操作均以服务端上下文中的用户身份为准，避免客户端跨用户读取或修改会话。</p>
 */
@RequestMapping("/api/v1/chat/conversation")
public interface IConversationManageController {

    /**
     * 查询当前用户的会话列表。
     *
     * @param request 可选查询请求体，包含分页或筛选条件
     * @return 当前用户可访问的会话摘要列表
     */
    @PostMapping("/list")
    List<ConversationSessionVO> list(@RequestBody(required = false) ConversationQueryRequest request);

    /**
     * 按名称或会话编码搜索当前用户的会话。
     *
     * @param request 搜索请求体，包含关键字；为空时返回全部会话
     * @return 匹配关键字的会话摘要列表
     */
    @PostMapping("/search")
    List<ConversationSessionVO> search(@RequestBody ConversationSearchRequest request);

    /**
     * 重命名当前用户拥有的会话。
     *
     * @param request 重命名请求体，包含会话定位和新的展示名称
     * @return 修改后的会话摘要
     */
    @PostMapping("/rename")
    ConversationSessionVO rename(@RequestBody ConversationRenameRequest request);

    /**
     * 设置或取消当前用户会话的置顶状态。
     *
     * @param request 置顶请求体，包含会话定位和目标置顶状态
     * @return 更新后的会话摘要
     */
    @PostMapping("/pin")
    ConversationSessionVO pin(@RequestBody ConversationPinRequest request);

    /**
     * 删除当前用户拥有的会话及关联历史。
     *
     * @param request 删除请求体，包含待删除会话的定位信息
     * @return 是否成功完成删除
     */
    @PostMapping("/delete")
    Boolean delete(@RequestBody ConversationDeleteRequest request);

    /** 查询当前用户可用的会话分组。 */
    @PostMapping("/group/list")
    List<ConversationGroupVO> listGroups();

    /** 创建当前用户的会话分组。 */
    @PostMapping("/group/create")
    ConversationGroupVO createGroup(@RequestBody(required = false) ConversationGroupCreateRequest request);

    /** 重命名当前用户的会话分组。 */
    @PostMapping("/group/rename")
    ConversationGroupVO renameGroup(@RequestBody(required = false) ConversationGroupRenameRequest request);

    /** 删除分组并将其中会话移动到未分组，不删除聊天历史。 */
    @PostMapping("/group/delete")
    Boolean deleteGroup(@RequestBody(required = false) ConversationGroupDeleteRequest request);

    /** 将当前用户的会话移动到目标分组；目标为空表示未分组。 */
    @PostMapping("/group/assign")
    ConversationSessionVO assignGroup(@RequestBody(required = false) ConversationGroupAssignRequest request);
}
