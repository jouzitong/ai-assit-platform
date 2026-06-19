package ai.platform.aiassist.service.ai.kb.entity.dto;

import ai.platform.aiassist.service.ai.api.enums.AiKbVersionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbVersionDTO extends BaseDTO {

    private String kbCode;

    private Integer versionNo;

    private String versionName;

    private AiKbVersionStatus status;

    private LocalDateTime publishedAt;

    private Long publishedBy;

    private String remark;
}
