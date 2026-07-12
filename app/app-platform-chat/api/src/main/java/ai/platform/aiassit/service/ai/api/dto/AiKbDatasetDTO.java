package ai.platform.aiassit.service.ai.api.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** 知识库提供方 Dataset 信息。 */
@Data
public class AiKbDatasetDTO implements Serializable {

    /** 提供方 Dataset ID，也是业务侧使用的 kbId。 */
    private String kbId;

    /** Dataset 名称。 */
    private String kbName;

    /** 知识库提供方类型。 */
    private AiKnowledgeClientType clientType;

    /** 知识库描述。 */
    private String description;

    /** 向量化模型标识。 */
    private String embeddingModel;

    /** 切片方式。 */
    private String chunkMethod;

    /** 提供方侧权限类型。 */
    private String permission;

    /** 文档数量。 */
    private Integer documentCount;

    /** Chunk 数量。 */
    private Integer chunkCount;

    /** 提供方返回的其余字段。 */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
