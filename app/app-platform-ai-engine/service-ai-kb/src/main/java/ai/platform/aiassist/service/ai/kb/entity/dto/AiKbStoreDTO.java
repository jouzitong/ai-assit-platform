package ai.platform.aiassist.service.ai.kb.entity.dto;

import ai.platform.aiassist.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassist.service.ai.api.enums.AiKbStoreStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbStoreDTO extends BaseDTO {

    private String kbCode;

    private String kbName;

    private AiKbBizType bizType;

    private String bizKey;

    private String providerCode;

    private String providerKbId;

    private Long currentVersionId;

    private Integer currentVersionNo;

    private AiKbStoreStatus status;

    private Boolean enabled;

    private Map<String, Object> configJson;

    private Map<String, Object> extJson;

    private LocalDateTime lastPublishAt;

    private String remark;
}
