package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DbQueryGetExt {

    private List<String> fields = new ArrayList<>();

    private List<DbQueryRelation> relations = new ArrayList<>();
}
