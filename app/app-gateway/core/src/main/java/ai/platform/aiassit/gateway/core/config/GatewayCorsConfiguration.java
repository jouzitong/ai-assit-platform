package ai.platform.aiassit.gateway.core.config;

import org.athena.framework.security.api.spi.TokenManager;
import org.athena.framework.security.auth.core.config.SecurityAuthProperties;
import org.athena.framework.security.auth.core.filter.GatewayTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GatewayCorsConfiguration implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "http://0.0.0.0:*"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Bean
    @Order(-120)
    public GatewayTokenFilter gatewayTokenFilter(SecurityAuthProperties properties, TokenManager tokenManager) {
        return new GatewayTokenFilter(properties, tokenManager);
    }

}
