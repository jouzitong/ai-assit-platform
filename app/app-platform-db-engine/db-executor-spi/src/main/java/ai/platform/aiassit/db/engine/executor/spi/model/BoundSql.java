package ai.platform.aiassit.db.engine.executor.spi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** SQL 方言渲染后的可执行语句。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoundSql {

    private String sql;

    @Builder.Default
    private List<Object> parameters = new ArrayList<>();
}
