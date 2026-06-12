package ai.platform.aiassit.db.engine.meta.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbTableMetaDTO extends BaseDTO {

    private String sourceKey;

    private String tableName;

    private String tableComment;

    private String tableType;

    private String layerType;

    private Long rowCount;

    private Integer columnCount;

    private String partitionKey;

    private Integer freshnessSeconds;

    private String status;

    private Boolean enabled;

    private LocalDateTime lastScanAt;

    private LocalDateTime lastSyncAt;

    private String remark;
}
