package ai.platform.aiassit.data.virtualization.spi.command;

import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PhysicalCommandSpec(
        PhysicalCommandType type,
        String table,
        List<Map<String, Object>> rows,
        Map<String, Object> assignments,
        PhysicalFilter filter
) {
    public PhysicalCommandSpec {
        rows = rows == null ? List.of() : List.copyOf(rows);
        assignments = assignments == null ? Map.of() : new LinkedHashMap<>(assignments);
    }
}
