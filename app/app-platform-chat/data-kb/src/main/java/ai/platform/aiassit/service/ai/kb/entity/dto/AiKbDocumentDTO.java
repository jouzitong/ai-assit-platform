package ai.platform.aiassit.service.ai.kb.entity.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassit.service.ai.api.enums.AiKbContentFormat;
import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentStatus;
import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentType;
import ai.platform.aiassit.service.ai.api.enums.AiKbProviderSyncStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbDocumentDTO extends BaseDTO {

    private String kbCode;

    private String documentCode;

    private String documentName;

    private AiKbDocumentType documentType;

    private AiKbBizType bizType;

    private String bizKey;

    private AiKbDocumentStatus status;

    private String providerDocumentId;

    private AiKbProviderSyncStatus providerSyncStatus;

    private Integer documentVersionNo;

    private String contentChecksum;

    private AiKbContentFormat contentFormat;

    private Long contentSize;

    private Map<String, Object> metaJson;

    private LocalDateTime lastGeneratedAt;

    private LocalDateTime updateTime;

    private String lastError;

    private String remark;
}
