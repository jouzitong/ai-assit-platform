package ai.platform.aiassit.knowledge.manage.service;

import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbPublishTaskDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

public interface AiKbPublishTaskService extends IMapperService<AiKbPublishTaskDTO> {

    AiKbPublishTaskDTO getByTaskCode(String taskCode);
}
