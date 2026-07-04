package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 混合检索命中结果对象。
 *
 * <p>用于描述一次混合检索中命中的单条文档内容，包含文档标识、
 * 命中文本、来源类型、相关性得分、重排序得分、最终得分以及扩展元数据。</p>
 */
@Data
public class HybridSearchHit implements Serializable {

    /**
     * 文档ID。
     */
    private String documentId;

    /**
     * 命中的文档内容或片段内容。
     */
    private String content;

    /**
     * 来源类型，例如文档、表结构、字段说明或业务知识等。
     */
    private String sourceType;

    /**
     * 原始检索相关性得分。
     */
    private Double score;

    /**
     * 重排序后的相关性得分。
     */
    private Double rerankScore;

    /**
     * 最终用于排序和展示的综合得分。
     */
    private Double finalScore;

    /**
     * 扩展元数据，用于存放来源文件、页码、表名、字段名等附加信息。
     */
    private Map<String, Object> metadata = new HashMap<>();
}
