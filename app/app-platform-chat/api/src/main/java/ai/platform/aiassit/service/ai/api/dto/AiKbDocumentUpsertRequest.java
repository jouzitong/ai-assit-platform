package ai.platform.aiassit.service.ai.api.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentType;
import ai.platform.aiassit.service.ai.api.enums.AiKbBizType;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiKbDocumentUpsertRequest implements Serializable {

    /**
     * 本地知识库业务编码。
     */
    private String kbCode;

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
     * 业务类型，
     * <p>
     * 非必填；未传时默认按 documentType 关联的 bizType 推导。
     */
    private AiKbBizType bizType;

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
     * 文档是否启用。
     *
     * <p>为空时新增文档默认启用、更新文档保持原状态；显式传 false 可用于创建尚未允许同步的草稿文档。</p>
     */
    private Boolean enabled;

    /**
     * 扩展参数。
     */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
