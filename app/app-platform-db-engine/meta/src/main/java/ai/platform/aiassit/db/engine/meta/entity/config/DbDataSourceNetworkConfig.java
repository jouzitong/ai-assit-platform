package ai.platform.aiassit.db.engine.meta.entity.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源网络配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbDataSourceNetworkConfig {

    /** 连接超时时间，单位毫秒。 */
    private Integer connectTimeoutMs;

    /** 读取超时时间，单位毫秒。 */
    private Integer readTimeoutMs;

    /** 写入超时时间，单位毫秒。 */
    private Integer writeTimeoutMs;
}
