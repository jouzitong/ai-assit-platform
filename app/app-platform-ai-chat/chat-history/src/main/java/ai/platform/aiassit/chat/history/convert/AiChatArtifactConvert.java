package ai.platform.aiassit.chat.history.convert;

import ai.platform.aiassit.chat.history.entity.AiChatArtifactEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiChatArtifactConvert extends IConvert<AiChatArtifactEntity, AiChatArtifactDTO> {
}
