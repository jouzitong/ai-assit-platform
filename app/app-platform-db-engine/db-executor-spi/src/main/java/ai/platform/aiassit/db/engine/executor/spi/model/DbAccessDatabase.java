package ai.platform.aiassit.db.engine.executor.spi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbAccessDatabase {

    private String host;

    private Integer port;

    private String databaseName;

    private String schemaName;

    private String jdbcUrl;
}
