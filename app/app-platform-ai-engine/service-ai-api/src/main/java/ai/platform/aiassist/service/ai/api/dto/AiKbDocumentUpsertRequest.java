package ai.platform.aiassist.service.ai.api.dto;

import ai.platform.aiassist.service.ai.api.enums.AiKbDocumentType;
import ai.platform.aiassist.service.ai.api.enums.AiKbSourceType;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiKbDocumentUpsertRequest implements Serializable {

    /**
     * 本地知识库标识。
     */
    private String kbId;

    /**
     * 文档唯一标识。
     */
    private String documentId;

    /**
     * 文档名称。
     */
    private String documentName;

    /**
     * 文档类型。
     */
    private AiKbDocumentType documentType;

    /**
     * 来源对象类型，
     * <p>
     * 非必填；未传时默认按 documentType 关联的 sourceType 推导。
     */
    private AiKbSourceType sourceType;

    /**
     * 来源对象唯一键。
     */
    private String sourceKey;

    /**
     * 文档正文内容。
     */
    private String content;

    /**
     * 扩展参数。
     */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
