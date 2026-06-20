package ai.platform.aiassist.service.ai.kb.entity.dto;

import ai.platform.aiassist.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassist.service.ai.api.enums.AiKbContentFormat;
import ai.platform.aiassist.service.ai.api.enums.AiKbDocumentStatus;
import ai.platform.aiassist.service.ai.api.enums.AiKbDocumentType;
import ai.platform.aiassist.service.ai.api.enums.AiKbReviewStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbDocumentDTO extends BaseDTO {

    private String kbCode;

    private Long kbVersionId;

    private String documentCode;

    private String documentName;

    private AiKbDocumentType documentType;

    private AiKbBizType bizType;

    private String bizKey;

    private String sourceSystem;

    private AiKbDocumentStatus status;

    private Integer draftVersionNo;

    private String contentChecksum;

    private AiKbContentFormat contentFormat;

    private Long contentSize;

    private Map<String, Object> metaJson;

    private AiKbReviewStatus reviewStatus;

    private LocalDateTime lastGeneratedAt;

    private String lastError;

    private String remark;
}
