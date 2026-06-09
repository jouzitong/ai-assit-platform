package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class DbQueryTreeNode {

    private Object id;

    private Object parentId;

    private String label;

    private Map<String, Object> data = new LinkedHashMap<>();

    private List<DbQueryTreeNode> children = new ArrayList<>();
}
