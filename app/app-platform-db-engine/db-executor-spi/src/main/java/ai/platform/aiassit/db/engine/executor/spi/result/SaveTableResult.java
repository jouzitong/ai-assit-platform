package ai.platform.aiassit.db.engine.executor.spi.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveTableResult {

    private String tableName;

    private Boolean created;

    private Boolean updated;

    private List<String> executedSqls;
}
