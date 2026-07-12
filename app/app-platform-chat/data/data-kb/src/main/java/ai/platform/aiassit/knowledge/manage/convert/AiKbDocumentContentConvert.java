package ai.platform.aiassit.knowledge.manage.convert;

import ai.platform.aiassit.knowledge.manage.entity.document.AiKbDocumentContentEntity;
import ai.platform.aiassit.knowledge.manage.entity.document.dto.AiKbDocumentContentDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbDocumentContentConvert extends IConvert<AiKbDocumentContentEntity, AiKbDocumentContentDTO> {
}
