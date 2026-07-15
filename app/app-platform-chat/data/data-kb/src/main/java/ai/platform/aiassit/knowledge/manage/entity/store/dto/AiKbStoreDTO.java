package ai.platform.aiassit.knowledge.manage.entity.store.dto;

import ai.platform.aiassit.service.ai.api.dto.AiKbAuthConfig;
import ai.platform.aiassit.service.ai.api.enums.AiKbStoreSyncStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbStoreDTO extends BaseDTO {

    private String kbCode;

    private String kbName;

    private String providerKbId;

    private String description;

    private String embeddingModel;

    private String permission;

    private String chunkMethod;

    private Map<String, Object> parserConfig;

    private String parseType;

    private String pipelineId;

    private Boolean enabled;

    private AiKbStoreSyncStatus syncStatus;

    private String syncError;

    private LocalDateTime lastSyncAt;

    private List<String> tags;

    private AiKbAuthConfig auth;

    private Map<String, Object> extJson;
}
