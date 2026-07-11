package ai.platform.aiassit.db.engine.meta.entity.config;

import ai.platform.aiassit.db.engine.meta.enums.DataSourceConfigType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/** 仅用于 HTTP API 数据源的配置。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonDeserialize(using = JsonDeserializer.None.class)
public class HttpApiSourceConfig implements DataSourceConfig {

    @Builder.Default
    private Integer configVersion = 2;

    @Builder.Default
    private DataSourceConfigType configType = DataSourceConfigType.HTTP_API;

    private String baseUrl;

    private DbDataSourceAuthConfig credential;

    private DbDataSourceNetworkConfig network;

    /** HTTP 请求头、响应路径等协议属性。 */
    @Builder.Default
    private Map<String, Object> attributes = new LinkedHashMap<>();
}
