package ai.platform.aiassist.service.ai.meta.domainservice;

import ai.platform.aiassist.service.ai.meta.entity.dto.AiModelManageDTO;
import ai.platform.aiassist.service.ai.meta.entity.req.AiModelManageQueryRequest;
import ai.platform.aiassist.service.ai.meta.entity.vo.AiModelManageVO;
import org.athena.framework.data.jdbc.vo.PageResultVO;

public interface AiModelManageDomainService {

    PageResultVO<AiModelManageVO> page(AiModelManageQueryRequest query);

    AiModelManageVO get(Long id);

    AiModelManageVO add(AiModelManageDTO dto);

    AiModelManageVO update(Long id, AiModelManageDTO dto);

    AiModelManageVO edit(Long id, AiModelManageDTO dto);

    boolean delete(Long id);
}
