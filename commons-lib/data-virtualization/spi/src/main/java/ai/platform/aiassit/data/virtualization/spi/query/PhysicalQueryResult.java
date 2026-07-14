package ai.platform.aiassit.data.virtualization.spi.query;

import java.util.List;
import java.util.Map;

public record PhysicalQueryResult(
        List<Map<String, Object>> rows,
        boolean exhausted,
        boolean truncated,
        long scannedRows,
        String nextCursor,
        long executionMs
) {
    public PhysicalQueryResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
