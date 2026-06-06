package ai.platform.aiassist.service.ai.meta.domainservice;

import ai.platform.aiassist.service.ai.meta.entity.dto.AiModelManageDTO;
import ai.platform.aiassist.service.ai.meta.entity.req.AiModelManageQueryRequest;
import org.athena.framework.data.jdbc.vo.PageResultVO;

public interface AiModelManageDomainService {

    PageResultVO<AiModelManageDTO> page(AiModelManageQueryRequest query);

    AiModelManageDTO get(Long id);

    AiModelManageDTO add(AiModelManageDTO dto);

    AiModelManageDTO update(Long id, AiModelManageDTO dto);

    AiModelManageDTO edit(Long id, AiModelManageDTO dto);

    boolean delete(Long id);
}
