package ai.platform.aiassist.service.ai.kb.entity.dto;

import ai.platform.aiassist.service.ai.api.enums.AiKbProviderSyncStatus;
import ai.platform.aiassist.service.ai.api.enums.AiKbPublishType;
import ai.platform.aiassist.service.ai.api.enums.AiKbVersionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbVersionDTO extends BaseDTO {

    private String kbCode;

    private Integer versionNo;

    private String versionName;

    private AiKbVersionStatus status;

    private AiKbPublishType publishType;

    private Map<String, Object> sourceSnapshotJson;

    private Map<String, Object> summaryJson;

    private AiKbProviderSyncStatus providerSyncStatus;

    private LocalDateTime providerSyncAt;

    private Map<String, Object> providerSyncResultJson;

    private String draftCreatedBy;

    private String publishedBy;

    private LocalDateTime publishedAt;

    private Long rollbackFromVersionId;

    private String remark;
}
