package ai.platform.aiassist.service.ai.meta.convert;

import ai.platform.aiassist.service.ai.meta.entity.AiProviderConfigEntity;
import ai.platform.aiassist.service.ai.meta.entity.dto.AiProviderConfigDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiProviderConfigConvert extends IConvert<AiProviderConfigEntity, AiProviderConfigDTO> {

}
