package ai.platform.aiassit.gateway.core.config;

import org.athena.framework.security.api.spi.TokenManager;
import org.athena.framework.security.auth.core.config.SecurityAuthProperties;
import org.athena.framework.security.auth.core.filter.GatewayTokenFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class GatewayCorsConfiguration {

    private static final List<String> ALLOWED_ORIGIN_PATTERNS = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://0.0.0.0:*"
    );

    private static final List<String> ALLOWED_METHODS = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
    );

    /**
     * 在认证过滤器之前处理 CORS，确保预检请求可以通过，且认证过滤器提前返回的 401/403 仍带有跨域响应头。
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> gatewayCorsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(-300);
        return registration;
    }

    @Bean
    @Order(-120)
    public GatewayTokenFilter gatewayTokenFilter(SecurityAuthProperties properties, TokenManager tokenManager) {
        return new GatewayTokenFilter(properties, tokenManager);
    }

}
