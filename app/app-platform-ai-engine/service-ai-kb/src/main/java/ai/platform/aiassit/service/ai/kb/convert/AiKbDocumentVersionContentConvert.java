package ai.platform.aiassit.service.ai.kb.convert;

import ai.platform.aiassit.service.ai.kb.entity.AiKbDocumentVersionContentEntity;
import ai.platform.aiassit.service.ai.kb.entity.dto.AiKbDocumentVersionContentDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbDocumentVersionContentConvert extends IConvert<AiKbDocumentVersionContentEntity, AiKbDocumentVersionContentDTO> {
}
