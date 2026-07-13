package ai.platform.aiassit.db.engine.meta.entity.dto;

import lombok.Data;

@Data
public class DbTableMetaCascadeDeleteResultDTO {

    private String sourceKey;

    private String tableName;

    private Integer deletedTableCount;

    private Integer deletedFieldCount;

    private Integer deletedIndexCount;

    private Integer deletedRelationCount;
}
