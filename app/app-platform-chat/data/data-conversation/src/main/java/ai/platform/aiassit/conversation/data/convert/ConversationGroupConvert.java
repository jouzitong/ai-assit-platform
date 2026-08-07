package ai.platform.aiassit.conversation.data.convert;

import ai.platform.aiassit.conversation.data.entity.ConversationGroupEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationGroupDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

/** 分组 entity / DTO 转换器。 */
@Mapper(componentModel = "spring")
public interface ConversationGroupConvert extends IConvert<ConversationGroupEntity, ConversationGroupDTO> {
}
