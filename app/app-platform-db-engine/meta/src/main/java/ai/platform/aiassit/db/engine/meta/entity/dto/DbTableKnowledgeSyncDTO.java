package ai.platform.aiassit.db.engine.meta.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DbTableKnowledgeSyncDTO {

    private String kbId;

    private String sourceKey;

    private Integer totalCount;

    private Integer createdCount;

    private Integer updatedCount;

    private Integer unchangedCount;

    private List<DbTableKnowledgeSyncItemDTO> items = new ArrayList<>();
}
