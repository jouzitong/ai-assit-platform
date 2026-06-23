package ai.platform.aiassit.db.engine.core.controller.req;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DbAccessTableListRequest {

    private String sourceKey;

    private List<String> tables = new ArrayList<>();
}
