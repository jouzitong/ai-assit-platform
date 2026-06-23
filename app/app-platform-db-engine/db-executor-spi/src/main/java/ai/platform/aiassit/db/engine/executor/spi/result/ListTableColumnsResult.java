package ai.platform.aiassit.db.engine.executor.spi.result;

import ai.platform.aiassit.db.engine.executor.spi.model.DbColumnMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListTableColumnsResult {

    private String tableName;

    private List<DbColumnMeta> columns;
}
