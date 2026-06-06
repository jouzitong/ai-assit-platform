package ai.platform.aiassist.service.ai.meta.convert;

import ai.platform.aiassist.service.ai.meta.entity.AiModelCredentialEntity;
import ai.platform.aiassist.service.ai.meta.entity.dto.AiModelCredentialDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiModelCredentialConvert extends IConvert<AiModelCredentialEntity, AiModelCredentialDTO> {

}
