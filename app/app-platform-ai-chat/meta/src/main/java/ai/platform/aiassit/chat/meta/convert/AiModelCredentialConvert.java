package ai.platform.aiassit.chat.meta.convert;

import ai.platform.aiassit.chat.meta.entity.AiModelCredentialEntity;
import ai.platform.aiassit.chat.meta.entity.dto.AiModelCredentialDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiModelCredentialConvert extends IConvert<AiModelCredentialEntity, AiModelCredentialDTO> {

}
