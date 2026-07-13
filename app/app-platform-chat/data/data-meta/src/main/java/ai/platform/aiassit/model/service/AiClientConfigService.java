package ai.platform.aiassit.model.service;

import ai.platform.aiassit.model.entity.dto.AiClientConfigDTO;
import ai.platform.aiassit.model.entity.vo.AiClientConfigVO;

import java.util.List;

public interface AiClientConfigService {
    List<AiClientConfigVO> list();
    AiClientConfigDTO require(Long id);
    AiClientConfigVO add(AiClientConfigDTO dto);
    AiClientConfigVO update(Long id, AiClientConfigDTO dto);
    boolean delete(Long id);
}
