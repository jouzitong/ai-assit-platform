package ai.platform.aiassist.service.ai.kb.convert;

import ai.platform.aiassist.service.ai.kb.entity.AiKbPublishTaskEntity;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbPublishTaskDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbPublishTaskConvert extends IConvert<AiKbPublishTaskEntity, AiKbPublishTaskDTO> {
}
