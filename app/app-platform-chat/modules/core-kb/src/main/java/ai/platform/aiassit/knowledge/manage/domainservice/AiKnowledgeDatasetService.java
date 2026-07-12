package ai.platform.aiassit.knowledge.manage.domainservice;

import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetSaveRequest;

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

    /**
     * 根据系统配置中的客户端标识查询 Provider Dataset。
     *
     * @param clientKey 系统参数 {@code chat.engine.kb.client.list} 中的客户端 key
     * @param request Dataset 查询条件
     * @return Dataset 列表
     */
    List<AiKbDatasetDTO> listDatasets(String clientKey, AiKbDatasetListRequest request);

    /** 创建 Provider Dataset。 */
    AiKbDatasetDTO createDataset(AiKbDatasetSaveRequest request);

    /** 使用系统配置中的客户端创建 Provider Dataset。 */
    AiKbDatasetDTO createDataset(String clientKey, AiKbDatasetSaveRequest request);

    /** 更新 Provider Dataset。 */
    AiKbDatasetDTO updateDataset(String kbId, AiKbDatasetSaveRequest request);

    /** 使用系统配置中的客户端更新 Provider Dataset。 */
    AiKbDatasetDTO updateDataset(String clientKey, String kbId, AiKbDatasetSaveRequest request);

    /** 删除 Provider Dataset。 */
    int deleteDatasets(AiKbDatasetDeleteRequest request);

    /** 使用系统配置中的客户端删除 Provider Dataset。 */
    int deleteDatasets(String clientKey, AiKbDatasetDeleteRequest request);
}
