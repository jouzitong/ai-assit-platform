package ai.platform.aiassit.chat.meta.convert;

import ai.platform.aiassit.chat.meta.entity.AiModelConfigEntity;
import ai.platform.aiassit.chat.meta.entity.dto.AiModelConfigDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiModelConfigConvert extends IConvert<AiModelConfigEntity, AiModelConfigDTO> {

}
