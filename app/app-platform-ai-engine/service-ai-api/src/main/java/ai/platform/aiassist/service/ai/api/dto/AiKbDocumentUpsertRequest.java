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
     * 文档已存在时是否允许覆盖更新。
     *
     * <p>默认不允许，避免上游重复同步覆盖本系统内人工维护过的草稿内容。</p>
     */
    private Boolean canUpdate = Boolean.FALSE;

    /**
     * 扩展参数。
     */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
