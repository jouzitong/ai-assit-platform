package ai.platform.aiassit.db.engine.core.controller.resp;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import lombok.Data;

@Data
public class DbAccessTableRemoteItem {

    private String tableName;

    private String tableComment;

    private String tableType;

    private Boolean synced;

    private DbTableMetaDTO tableMeta;
}
