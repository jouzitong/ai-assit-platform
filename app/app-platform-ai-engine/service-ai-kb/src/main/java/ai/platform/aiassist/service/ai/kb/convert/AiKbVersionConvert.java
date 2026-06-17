package ai.platform.aiassist.service.ai.kb.convert;

import ai.platform.aiassist.service.ai.kb.entity.AiKbVersionEntity;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbVersionDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbVersionConvert extends IConvert<AiKbVersionEntity, AiKbVersionDTO> {
}
