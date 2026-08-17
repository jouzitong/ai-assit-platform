package ai.platform.aiassit.gateway.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "gateway.request-guard")
public class GatewayRequestGuardProperties {

    /**
     * 非生产环境默认关闭，生产环境由过滤器强制开启；非生产环境可显式配置开启。
     */
    private boolean enabled = false;

    private String headerName = "X-Frontend-Environment";

    /**
     * 允许多个前端环境值，比较时忽略大小写并去除首尾空白。
     */
    private List<String> expectedValue = new ArrayList<>();

    private List<String> ignoreUrls = new ArrayList<>(List.of(
        "/actuator/**",
        "/health/**",
        "/error",
        "/favicon.ico"
    ));
}
