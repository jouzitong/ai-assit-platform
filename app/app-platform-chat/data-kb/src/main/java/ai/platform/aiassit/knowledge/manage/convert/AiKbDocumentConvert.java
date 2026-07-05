package ai.platform.aiassit.knowledge.manage.convert;

import ai.platform.aiassit.knowledge.manage.entity.AiKbDocumentEntity;
import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbDocumentDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbDocumentConvert extends IConvert<AiKbDocumentEntity, AiKbDocumentDTO> {
}
