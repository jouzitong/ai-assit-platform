package ai.platform.aiassit.db.engine.meta.entity.config;

import ai.platform.aiassit.db.engine.meta.enums.DbDataSourceDbType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库类配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbDataSourceDatabaseConfig {

    /** 数据库类型，例如 mysql、postgresql、clickhouse。 */
    private DbDataSourceDbType dbType;

    /** 主机地址。 */
    private String host;

    /** 端口。 */
    private Integer port;

    /** 库名。 */
    private String databaseName;

    /** schema。 */
    private String schemaName;

    /** jdbc 地址。 */
    private String jdbcUrl;
}
