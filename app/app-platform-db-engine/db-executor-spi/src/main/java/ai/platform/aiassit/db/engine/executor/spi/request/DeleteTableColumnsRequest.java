package ai.platform.aiassit.db.engine.executor.spi.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteTableColumnsRequest {

    private String schemaName;

    private String tableName;

    private List<String> columnNames;
}
