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

    /** 文档是否已存在。 */
    private Boolean exists;

    /** 内容和元数据是否无变化。 */
    private Boolean unchanged;

    /** 当前文档版本号。 */
    private Integer currentVersionNo;

    /** 覆盖更新前的文档版本号。 */
    private Integer previousVersionNo;

    /** 状态说明。 */
    private String message;
}
