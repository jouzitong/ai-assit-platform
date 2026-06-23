package ai.platform.aiassit.db.engine.executor.spi.result;

import ai.platform.aiassit.db.engine.executor.spi.model.DbQueryColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResult {

    private List<DbQueryColumn> columns;

    private List<Map<String, Object>> rows;

    private Integer rowCount;

    private Long executionMs;
}
