package ai.platform.aiassit.service.ai.kb.convert;

import ai.platform.aiassit.service.ai.kb.entity.AiKbDocumentEntity;
import ai.platform.aiassit.service.ai.kb.entity.dto.AiKbDocumentDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbDocumentConvert extends IConvert<AiKbDocumentEntity, AiKbDocumentDTO> {
}
