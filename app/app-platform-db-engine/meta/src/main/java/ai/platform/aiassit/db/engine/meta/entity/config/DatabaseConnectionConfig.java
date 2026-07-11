package ai.platform.aiassit.db.engine.meta.entity.config;

import ai.platform.aiassit.db.engine.meta.enums.DatabaseConnectionMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据库连接地址；JDBC_URL 与 HOST_PORT 两种模式互斥。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseConnectionConfig {

    private DatabaseConnectionMode mode;

    private String jdbcUrl;

    private String host;

    private Integer port;

    private String databaseName;

    private String schemaName;
}
