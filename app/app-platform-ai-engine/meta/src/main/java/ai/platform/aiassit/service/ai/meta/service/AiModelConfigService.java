package ai.platform.aiassit.service.ai.meta.service;

import ai.platform.aiassit.service.ai.api.dto.AiEnabledModelDTO;
import ai.platform.aiassit.service.ai.meta.entity.dto.AiModelConfigDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.List;

public interface AiModelConfigService extends IMapperService<AiModelConfigDTO> {

    List<AiEnabledModelDTO> selectEnabledModels();
}
