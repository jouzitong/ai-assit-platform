package ai.platform.aiassit.chat.meta.convert;

import ai.platform.aiassit.chat.meta.entity.AiProviderConfigEntity;
import ai.platform.aiassit.chat.meta.entity.dto.AiProviderConfigDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiProviderConfigConvert extends IConvert<AiProviderConfigEntity, AiProviderConfigDTO> {

}
