package ai.platform.aiassit.knowledge.manage.domainservice;

import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;

import java.util.List;

/**
 * 平台侧知识库 Dataset 管理服务。
 *
 * <p>用于管理提供方侧 Dataset 与业务 kbId 的对应关系。后续新增、修改、删除 Dataset
 * 时继续扩展该服务，不放入文档执行型 {@code KnowledgeService}。</p>
 */
public interface AiKnowledgeDatasetService {

    /**
     * 查询知识库提供方 Dataset 列表。
     *
     * @param request 查询条件
     * @return Dataset 列表
     */
    List<AiKbDatasetDTO> listDatasets(AiKbDatasetListRequest request);
}
