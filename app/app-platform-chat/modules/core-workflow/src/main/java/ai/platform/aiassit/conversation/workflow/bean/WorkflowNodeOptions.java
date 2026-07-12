package ai.platform.aiassit.conversation.workflow.bean;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点基础配置。
 *
 * <p>用于承载节点级别的通用运行参数，避免继续以动态 Map 维护核心配置。</p>
 *
 * @author zhouzhitong
 * @since 2026/7/6
 */
@Data
@NoArgsConstructor
public class WorkflowNodeOptions implements Serializable {

    /**
     * 节点最低可接受置信度，低于该值时可由上层决定是否回退或终止。
     */
    private Double minConfidenceScore;

    /**
     * 节点允许的最大循环重试次数。
     */
    private Integer maxLoopCount;

    /**
     * 节点默认绑定的知识库 ID。
     */
    private String knowledgeBaseId;

    /**
     * 节点默认知识检索条数。
     */
    private Integer knowledgeTopK;

    /**
     * 节点超时时间，单位毫秒。
     */
    private Integer timeoutMs;

    /**
     * 预留扩展字段，承载未标准化的附加配置。
     */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
