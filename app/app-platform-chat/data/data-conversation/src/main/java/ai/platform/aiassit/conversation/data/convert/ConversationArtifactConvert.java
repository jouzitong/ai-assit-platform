package ai.platform.aiassit.conversation.data.convert;

import ai.platform.aiassit.conversation.data.entity.ConversationArtifactEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationArtifactDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConversationArtifactConvert extends IConvert<ConversationArtifactEntity, ConversationArtifactDTO> {
}
