package ai.platform.aiassit.model.domainservice;

import ai.platform.aiassit.model.entity.dto.AiModelManageDTO;
import ai.platform.aiassit.model.entity.dto.AiModelBatchSaveDTO;
import ai.platform.aiassit.model.entity.req.AiModelManageQueryRequest;
import ai.platform.aiassit.model.entity.vo.AiModelManageVO;
import org.athena.framework.data.jdbc.vo.PageResultVO;

public interface AiModelManageDomainService {

    PageResultVO<AiModelManageVO> page(AiModelManageQueryRequest query);

    AiModelManageVO get(Long id);

    AiModelManageVO add(AiModelManageDTO dto);

    java.util.List<AiModelManageVO> batchSave(AiModelBatchSaveDTO dto);

    AiModelManageVO update(Long id, AiModelManageDTO dto);

    AiModelManageVO edit(Long id, AiModelManageDTO dto);

    boolean delete(Long id);
}
