package ai.platform.aiassit.conversation.data.service;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationGroupDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.List;

/** 分组基础数据服务，只负责持久化和按所有者查询。 */
public interface ConversationGroupDataService extends IMapperService<ConversationGroupDTO> {

    List<ConversationGroupDTO> listByUserId(Long userId);

    ConversationGroupDTO getByUserIdAndCode(Long userId, String groupCode);
}
