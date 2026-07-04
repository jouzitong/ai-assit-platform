package ai.platform.aiassit.service.ai.kb.convert;

import ai.platform.aiassit.service.ai.kb.entity.AiKbStoreEntity;
import ai.platform.aiassit.service.ai.kb.entity.dto.AiKbStoreDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbStoreConvert extends IConvert<AiKbStoreEntity, AiKbStoreDTO> {
}
