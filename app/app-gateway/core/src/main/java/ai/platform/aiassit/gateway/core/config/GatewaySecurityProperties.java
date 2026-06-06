package ai.platform.aiassit.gateway.core.config;

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
        "/user/auth/**",
        "/auth/**",
        "/open/**",
        "/health/**",
        "/favicon.ico",
        "/error"
    ));

    private Permission permission = new Permission();

    private Signing signing = new Signing();

    @Data
    public static class Permission {

        private boolean enabled = false;

        private String appCode = "app-platform-user";

        private String requiredPermissionsHeader = "X-Gateway-Required-Permissions";
    }

    @Data
    public static class Signing {

        private String secret = "";
    }
}
