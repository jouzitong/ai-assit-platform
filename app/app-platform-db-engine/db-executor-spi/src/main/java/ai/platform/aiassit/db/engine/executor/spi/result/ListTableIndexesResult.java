package ai.platform.aiassit.db.engine.executor.spi.result;

import ai.platform.aiassit.db.engine.executor.spi.model.DbIndexMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListTableIndexesResult {

    private String tableName;

    private List<DbIndexMeta> indexes;
}
