package ai.platform.aiassit.service.ai.api.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识库提供方 Dataset 列表查询请求。
 *
 * <p>Dataset 是提供方侧的知识库实体，其 {@code kbId} 可保存为平台本地知识库的
 * {@code providerKbId}。</p>
 */
@Data
public class AiKbDatasetListRequest implements Serializable {

    /** 知识库提供方类型；当前默认使用 RAGFlow。 */
    private AiKnowledgeClientType clientType = AiKnowledgeClientType.RAGFLOW;

    /** 按 Dataset 名称模糊筛选。 */
    private String name;

    /** 页码，从 1 开始。 */
    private Integer page = 1;

    /** 每页数量。 */
    private Integer pageSize = 30;

    /** 是否包含提供方的文档解析状态。 */
    private Boolean includeParsingStatus = Boolean.TRUE;

    /** 提供方调用上下文，例如覆盖 RAGFlow 地址或 API Key。 */
    private RequestMeta meta = new RequestMeta();
}
