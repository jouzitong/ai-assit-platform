package ai.platform.aiassit.db.engine.executor.spi.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteRequest {

    private String sql;

    /** 与 SQL/命令信封中的占位符按顺序对应的参数。 */
    @Builder.Default
    private List<Object> parameters = new ArrayList<>();
}
