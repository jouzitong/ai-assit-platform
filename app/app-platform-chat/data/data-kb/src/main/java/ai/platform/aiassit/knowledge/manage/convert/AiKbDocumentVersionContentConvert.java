package ai.platform.aiassit.knowledge.manage.convert;

import ai.platform.aiassit.knowledge.manage.entity.document.AiKbDocumentVersionContentEntity;
import ai.platform.aiassit.knowledge.manage.entity.document.dto.AiKbDocumentVersionContentDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbDocumentVersionContentConvert extends IConvert<AiKbDocumentVersionContentEntity, AiKbDocumentVersionContentDTO> {
}
