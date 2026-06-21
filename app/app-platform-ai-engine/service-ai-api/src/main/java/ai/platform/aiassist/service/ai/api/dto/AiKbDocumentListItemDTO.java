package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AiKbDocumentListItemDTO implements Serializable {

    private Long id;

    private String kbCode;

    private String documentCode;

    private String documentName;

    private String documentType;

    private String bizType;

    private String bizKey;

    private String sourceSystem;

    private String status;

    private String providerDocumentId;

    private String providerSyncStatus;

    private Integer currentVersionNo;

    private String contentFormat;

    private Long contentSize;

    private LocalDateTime lastGeneratedAt;
}
