package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class KbSearchRequest implements Serializable {

    /** 本地知识库业务编码。服务端据此查询对应的 Provider 知识库 ID。 */
    private String kbCode;

    /**
     * @deprecated 请改用 {@link #kbCode}。该字段仅兼容历史调用，值仍按本地 kbCode 解析，
     * 不接受 Provider/RAGFlow 的真实知识库 ID。
     */
    @Deprecated(since = "2026-07", forRemoval = true)
    private String kbId;
    /** 用户检索语句 */
    private String query;
    /** 返回命中条数上限 */
    private Integer topK = 5;
    /** RAGFlow 结果页码；为空时使用 Provider 默认值 */
    private Integer page;
    /** RAGFlow 每页返回数量；为空时兼容使用 topK */
    private Integer pageSize;
    /** 参与向量相似度计算的候选 Chunk 数量 */
    private Integer retrievalTopK;
    /** 最低混合相似度阈值，取值范围 0 到 1 */
    private Double similarityThreshold;
    /** 向量相似度权重，取值范围 0 到 1 */
    private Double vectorSimilarityWeight;
    /** 可选的 Rerank 模型标识 */
    private String rerankId;
    /** 是否启用关键词增强 */
    private Boolean keyword;
    /** 是否返回高亮内容 */
    private Boolean highlight;
    /** 是否启用知识图谱召回 */
    private Boolean useKg;
    /** 是否启用目录增强召回 */
    private Boolean tocEnhance;
    /** 限定检索的 Provider 文档 ID */
    private List<String> documentIds = new ArrayList<>();
    /** 跨语言检索目标语言 */
    private List<String> crossLanguages = new ArrayList<>();
    /** RAGFlow 元数据过滤条件 */
    private Map<String, Object> metadataCondition = new LinkedHashMap<>();
    /** 请求上下文信息 */
    private RequestMeta meta = new RequestMeta();
}
