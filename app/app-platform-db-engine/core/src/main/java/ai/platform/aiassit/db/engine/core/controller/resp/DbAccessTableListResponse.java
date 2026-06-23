package ai.platform.aiassit.db.engine.core.controller.resp;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DbAccessTableListResponse {

    private String sourceKey;

    private List<DbAccessTableRemoteItem> tables = new ArrayList<>();
}
