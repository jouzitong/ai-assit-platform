package ai.platform.aiassit.service.ai.spi;

import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetSaveRequest;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;

import java.util.List;

/**
 * 知识库提供方 Dataset 管理 SPI。
 *
 * <p>该 SPI 只负责提供方侧 Dataset（即业务侧 kbId）的 CRUD，不承载文档写入、
 * 检索、向量化等执行能力；后者仍由 {@link KnowledgeService} 负责。</p>
 */
public interface KnowledgeDatasetService {

    /**
     * 获取当前 Dataset 管理服务支持的提供方类型。
     *
     * @return 知识库提供方类型
     */
    AiKnowledgeClientType knowledgeClientType();

    /**
     * 查询提供方侧 Dataset 列表。
     *
     * @param request 查询条件
     * @return Dataset 列表，其中 {@code kbId} 为提供方 Dataset ID
     */
    List<AiKbDatasetDTO> listDatasets(AiKbDatasetListRequest request);

    /**
     * 创建提供方侧 Dataset。
     *
     * @param request Dataset 配置
     * @return 新建的 Dataset，其中 {@code kbId} 为提供方 Dataset ID
     */
    AiKbDatasetDTO createDataset(AiKbDatasetSaveRequest request);

    /**
     * 更新提供方侧 Dataset 配置。
     *
     * @param kbId 提供方 Dataset ID
     * @param request 待更新的 Dataset 配置
     * @return 更新后的 Dataset
     */
    AiKbDatasetDTO updateDataset(String kbId, AiKbDatasetSaveRequest request);

    /**
     * 删除提供方侧 Dataset。
     *
     * @param request 删除条件
     * @return 成功提交删除的 Dataset 数量
     */
    int deleteDatasets(AiKbDatasetDeleteRequest request);
}
