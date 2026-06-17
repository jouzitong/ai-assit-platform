package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiKbDocumentUpsertResponse implements Serializable {

    /** 本地知识库标识。 */
    private String kbId;

    /** 文档唯一标识。 */
    private String documentId;

    /** 是否新增。 */
    private Boolean created;

    /** 是否发生更新。 */
    private Boolean updated;

    /** 当前草稿版本号。 */
    private Integer draftVersionNo;
}
