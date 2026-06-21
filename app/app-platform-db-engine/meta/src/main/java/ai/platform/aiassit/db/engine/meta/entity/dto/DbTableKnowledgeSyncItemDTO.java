package ai.platform.aiassit.db.engine.meta.entity.dto;

import lombok.Data;

@Data
public class DbTableKnowledgeSyncItemDTO {

    private String tableName;

    private String documentId;

    private Boolean created;

    private Boolean updated;

    private Integer currentVersionNo;
}
