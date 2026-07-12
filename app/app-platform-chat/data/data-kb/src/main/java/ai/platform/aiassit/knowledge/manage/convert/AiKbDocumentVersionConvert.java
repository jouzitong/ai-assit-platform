package ai.platform.aiassit.knowledge.manage.convert;

import ai.platform.aiassit.knowledge.manage.entity.document.AiKbDocumentVersionEntity;
import ai.platform.aiassit.knowledge.manage.entity.document.dto.AiKbDocumentVersionDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbDocumentVersionConvert extends IConvert<AiKbDocumentVersionEntity, AiKbDocumentVersionDTO> {
}
