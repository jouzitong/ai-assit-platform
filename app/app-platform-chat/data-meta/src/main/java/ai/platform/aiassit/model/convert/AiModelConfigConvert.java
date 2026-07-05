package ai.platform.aiassit.model.convert;

import ai.platform.aiassit.model.entity.AiModelConfigEntity;
import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiModelConfigConvert extends IConvert<AiModelConfigEntity, AiModelConfigDTO> {

}
