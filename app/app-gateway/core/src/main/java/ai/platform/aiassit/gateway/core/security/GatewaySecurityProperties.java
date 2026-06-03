package ai.platform.aiassit.gateway.core.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    private boolean enabled = true;

    private boolean requireToken = true;

    private String headerName = "Authorization";

    private String tokenPrefix = "Bearer";

    private List<String> ignoreUrls = new ArrayList<>(List.of(
        "/actuator/**",
        "/health/**",
        "/favicon.ico",
        "/error"
    ));

    private Permission permission = new Permission();

    @Data
    public static class Permission {

        private boolean enabled = false;

        private String appCode = "app-platform-user";

        private String requiredPermissionsHeader = "X-Gateway-Required-Permissions";
    }
}
