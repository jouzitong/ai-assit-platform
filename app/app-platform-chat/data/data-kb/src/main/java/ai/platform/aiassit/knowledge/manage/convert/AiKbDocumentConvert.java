package ai.platform.aiassit.knowledge.manage.convert;

import ai.platform.aiassit.knowledge.manage.entity.document.AiKbDocumentEntity;
import ai.platform.aiassit.knowledge.manage.entity.document.dto.AiKbDocumentDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbDocumentConvert extends IConvert<AiKbDocumentEntity, AiKbDocumentDTO> {
}
