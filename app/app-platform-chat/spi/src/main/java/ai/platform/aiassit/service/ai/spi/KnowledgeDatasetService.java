package ai.platform.aiassit.service.ai.spi;

import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
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
}
