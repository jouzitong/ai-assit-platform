package ai.platform.aiassit.conversation.service;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationGroupDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupAssignRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupCreateRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupDeleteRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupRenameRequest;

import java.util.List;

/** 会话分组应用服务，负责用户归属、分组生命周期和 session 绑定规则。 */
public interface ConversationGroupService {

    List<ConversationGroupDTO> listGroups(Long userId);

    ConversationGroupDTO createGroup(Long userId, ConversationGroupCreateRequest request);

    ConversationGroupDTO renameGroup(Long userId, ConversationGroupRenameRequest request);

    Boolean deleteGroup(Long userId, ConversationGroupDeleteRequest request);

    ConversationSessionDTO assignSession(Long userId, ConversationGroupAssignRequest request);

    /** 校验新会话要绑定的分组，并返回规范化后的编码。 */
    String validateNewSessionGroup(Long userId, String groupCode, ConversationBusinessType businessType);

    /** 校验续聊请求中的分组不能改变已有 session 的持久化归属。 */
    void validateExistingSessionGroup(Long userId,
                                      ConversationSessionDTO session,
                                      String requestedGroupCode,
                                      ConversationBusinessType businessType);
}
