package ai.platform.aiassist.service.ai.meta.convert;

import ai.platform.aiassist.service.ai.meta.entity.AiModelConfigEntity;
import ai.platform.aiassist.service.ai.meta.entity.dto.AiModelConfigDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiModelConfigConvert extends IConvert<AiModelConfigEntity, AiModelConfigDTO> {

}
