package ai.platform.aiassit.model.service;

import ai.platform.aiassit.service.ai.api.dto.AiEnabledModelDTO;
import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.List;

public interface AiModelConfigService extends IMapperService<AiModelConfigDTO> {

    AiModelConfigDTO getByModelCode(String modelCode);

    List<AiEnabledModelDTO> selectEnabledModels();
}
