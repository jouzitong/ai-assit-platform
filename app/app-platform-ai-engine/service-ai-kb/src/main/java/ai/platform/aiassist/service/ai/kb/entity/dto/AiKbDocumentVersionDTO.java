package ai.platform.aiassist.service.ai.kb.entity.dto;

import ai.platform.aiassist.service.ai.api.enums.AiKbChangeType;
import ai.platform.aiassist.service.ai.api.enums.AiKbContentFormat;
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

    private Long kbVersionId;

    private Integer versionNo;

    private Integer documentVersionNo;

    private AiKbChangeType changeType;

    private String contentChecksum;

    private AiKbContentFormat contentFormat;

    private Long contentSize;

    private Map<String, Object> metaJson;

    private String sourceSystem;

    private LocalDateTime publishedAt;

    private String publishedBy;

    private String remark;
}
