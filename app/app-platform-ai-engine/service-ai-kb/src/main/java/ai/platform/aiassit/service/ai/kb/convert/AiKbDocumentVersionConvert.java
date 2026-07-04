package ai.platform.aiassit.service.ai.kb.convert;

import ai.platform.aiassit.service.ai.kb.entity.AiKbDocumentVersionEntity;
import ai.platform.aiassit.service.ai.kb.entity.dto.AiKbDocumentVersionDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbDocumentVersionConvert extends IConvert<AiKbDocumentVersionEntity, AiKbDocumentVersionDTO> {
}
