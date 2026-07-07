package ai.platform.aiassit.knowledge.manage.entity.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKbChangeType;
import ai.platform.aiassit.service.ai.api.enums.AiKbContentFormat;
import ai.platform.aiassit.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbDocumentVersionDTO extends BaseDTO {

    private String kbCode;

    private String documentCode;

    private String documentName;

    private AiKbDocumentType documentType;

    private AiKbBizType bizType;

    private String bizKey;

    private Integer documentVersionNo;

    private AiKbChangeType changeType;

    private String contentChecksum;

    private AiKbContentFormat contentFormat;

    private Long contentSize;

    private Map<String, Object> metaJson;

    private LocalDateTime snapshotAt;

    private String snapshotBy;

    private String remark;
}
