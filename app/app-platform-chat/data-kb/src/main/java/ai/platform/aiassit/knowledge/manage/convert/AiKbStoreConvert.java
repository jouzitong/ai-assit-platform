package ai.platform.aiassit.knowledge.manage.convert;

import ai.platform.aiassit.knowledge.manage.entity.AiKbStoreEntity;
import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbStoreDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbStoreConvert extends IConvert<AiKbStoreEntity, AiKbStoreDTO> {
}
