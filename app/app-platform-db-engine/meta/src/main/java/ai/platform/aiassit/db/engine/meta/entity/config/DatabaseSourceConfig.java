package ai.platform.aiassit.db.engine.meta.entity.config;

import ai.platform.aiassit.db.engine.meta.enums.DataSourceConfigType;
import ai.platform.aiassit.db.engine.meta.enums.DbDataSourceDbType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/** 仅用于数据库数据源的配置。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonDeserialize(using = JsonDeserializer.None.class)
public class DatabaseSourceConfig implements DataSourceConfig {

    @Builder.Default
    private Integer configVersion = 2;

    @Builder.Default
    private DataSourceConfigType configType = DataSourceConfigType.DATABASE;

    private DbDataSourceDbType dbType;

    private DatabaseConnectionConfig connection;

    /**
     * 当前阶段为兼容现有凭证结构保留；新配置优先只使用 credentialRef。
     */
    private DbDataSourceAuthConfig credential;

    private DbDataSourceNetworkConfig network;

    /** 驱动特有的非敏感连接参数。 */
    @Builder.Default
    private Map<String, Object> driverProperties = new LinkedHashMap<>();
}
