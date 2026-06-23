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
public class DeleteTableColumnsResult {

    private String tableName;

    private Integer affectedColumnCount;

    private List<String> executedSqls;
}
