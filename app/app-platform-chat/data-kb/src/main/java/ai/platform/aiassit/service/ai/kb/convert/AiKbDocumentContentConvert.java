package ai.platform.aiassit.service.ai.kb.convert;

import ai.platform.aiassit.service.ai.kb.entity.AiKbDocumentContentEntity;
import ai.platform.aiassit.service.ai.kb.entity.dto.AiKbDocumentContentDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbDocumentContentConvert extends IConvert<AiKbDocumentContentEntity, AiKbDocumentContentDTO> {
}
