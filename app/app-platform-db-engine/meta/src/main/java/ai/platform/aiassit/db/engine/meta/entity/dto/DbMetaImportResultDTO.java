package ai.platform.aiassit.db.engine.meta.entity.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 元数据导入结果。
 */
@Data
@Builder
public class DbMetaImportResultDTO {

    private Integer tableCreatedCount;

    private Integer tableUpdatedCount;

    private Integer fieldCreatedCount;

    private Integer fieldUpdatedCount;

    private Integer indexCreatedCount;

    private Integer indexUpdatedCount;
}
