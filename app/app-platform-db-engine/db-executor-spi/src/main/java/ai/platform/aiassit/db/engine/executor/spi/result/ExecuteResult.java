package ai.platform.aiassit.db.engine.executor.spi.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteResult {

    private Integer affectedRows;

    private Long executionMs;
}
