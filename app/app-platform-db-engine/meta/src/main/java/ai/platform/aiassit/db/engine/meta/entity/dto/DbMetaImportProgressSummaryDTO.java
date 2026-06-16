package ai.platform.aiassit.db.engine.meta.entity.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DbMetaImportProgressSummaryDTO {

    private Integer tableTotal;

    private Integer tableProcessed;

    private Integer tableCreatedCount;

    private Integer tableUpdatedCount;

    private Integer fieldTotal;

    private Integer fieldProcessed;

    private Integer fieldCreatedCount;

    private Integer fieldUpdatedCount;

    private Integer indexTotal;

    private Integer indexProcessed;

    private Integer indexCreatedCount;

    private Integer indexUpdatedCount;
}
