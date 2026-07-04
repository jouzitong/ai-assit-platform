package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 混合检索响应对象。
 *
 * <p>用于承载一次知识库混合检索的返回结果，包括检索请求信息、
 * 重排序状态、降级状态以及命中的检索结果列表。</p>
 */
@Data
public class HybridSearchResponse implements Serializable {

    /**
     * 知识库ID。
     */
    private String kbId;

    /**
     * 用户原始查询内容。
     */
    private String query;

    /**
     * 检索模式，例如向量检索、关键词检索或混合检索。
     */
    private String retrievalMode;

    /**
     * 是否已执行重排序。
     */
    private Boolean reranked = Boolean.FALSE;

    /**
     * 是否发生降级处理。
     */
    private Boolean degraded = Boolean.FALSE;

    /**
     * 降级原因说明。
     */
    private String degradedReason;

    /**
     * 检索命中的结果列表。
     */
    private List<HybridSearchHit> hits = new ArrayList<>();
}
