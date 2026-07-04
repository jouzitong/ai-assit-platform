package ai.platform.aiassist.service.ai.api.dto;

import ai.platform.aiassist.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassist.service.ai.api.enums.AiKbContentFormat;
import ai.platform.aiassist.service.ai.api.enums.AiKbDocumentStatus;
import ai.platform.aiassist.service.ai.api.enums.AiKbDocumentType;
import ai.platform.aiassist.service.ai.api.enums.AiKbProviderSyncStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AiKbDocumentListItemDTO implements Serializable {

    private Long id;

    private String kbCode;

    private String documentCode;

    private String documentName;

    private AiKbDocumentType documentType;

    private AiKbBizType bizType;

    private String bizKey;

    private String sourceSystem;

    private AiKbDocumentStatus status;

    private String providerDocumentId;

    private AiKbProviderSyncStatus providerSyncStatus;

    private Integer currentVersionNo;

    private AiKbContentFormat contentFormat;

    private Long contentSize;

    private LocalDateTime lastGeneratedAt;

    private LocalDateTime updateTime;
}
