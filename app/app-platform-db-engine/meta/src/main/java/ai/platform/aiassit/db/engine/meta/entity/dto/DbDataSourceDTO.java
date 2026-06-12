package ai.platform.aiassit.db.engine.meta.entity.dto;

import ai.platform.aiassit.db.engine.meta.entity.config.DbDataSourceConfig;
import ai.platform.aiassit.db.engine.meta.enums.DbDataSourceSyncMode;
import ai.platform.aiassit.db.engine.meta.enums.DbDataSourceType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbDataSourceDTO extends BaseDTO {

    private String sourceKey;

    private String sourceName;

    private DbDataSourceType sourceType;

    private String ownerTeam;

    private String ownerUser;

    private String status;

    private Boolean enabled;

    private DbDataSourceSyncMode syncMode;

    private DbDataSourceConfig config;

    private LocalDateTime lastSyncAt;

    private LocalDateTime lastAccessAt;

    private String summary;

    private String remark;
}
