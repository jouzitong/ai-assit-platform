package ai.platform.aiassit.db.engine.core.controller.resp;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DbAccessTableSyncResponse {

    private String sourceKey;

    private Boolean allowUpdate;

    private Integer createdTableCount;

    private Integer updatedTableCount;

    private Integer createdFieldCount;

    private Integer updatedFieldCount;

    private List<DbAccessTableSyncItem> items = new ArrayList<>();
}
