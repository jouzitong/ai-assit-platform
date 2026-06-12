package ai.platform.aiassit.db.engine.meta.entity.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 数据源配置对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbDataSourceConfig {

    /** 统一访问地址。 */
    private String endpoint;

    /** 连接与调用网络配置。 */
    private DbDataSourceNetworkConfig network;

    /** 认证配置。 */
    private DbDataSourceAuthConfig auth;

    /** 数据库类配置。 */
    private DbDataSourceDatabaseConfig database;

    /** 扩展属性。 */
    private Map<String, Object> attributes;
}
