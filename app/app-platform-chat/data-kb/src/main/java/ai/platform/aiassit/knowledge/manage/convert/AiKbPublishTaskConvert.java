package ai.platform.aiassit.knowledge.manage.convert;

import ai.platform.aiassit.knowledge.manage.entity.AiKbPublishTaskEntity;
import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbPublishTaskDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiKbPublishTaskConvert extends IConvert<AiKbPublishTaskEntity, AiKbPublishTaskDTO> {
}
