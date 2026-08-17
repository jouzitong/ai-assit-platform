package ai.platform.aiassit.gateway.core.filter;

import ai.platform.aiassit.gateway.core.config.GatewayRequestGuardProperties;
import ai.platform.aiassit.gateway.core.context.GatewaySecurityAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 在认证和路由之前校验前端构建环境，避免测试前端误调用生产网关。
 *
 * <p>pro/prod 是不可关闭的生产保护 Profile；其他 Profile 是否开启由配置决定。</p>
 */
@Component
@Order(-150)
public class GatewayRequestGuardFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRequestGuardFilter.class);

    private static final Set<String> MANDATORY_PRODUCTION_PROFILES = Set.of("pro", "prod");
    private static final String PRODUCTION_ENVIRONMENT = "prod";
    private static final String DEFAULT_DEVELOPMENT_ENVIRONMENT = "dev";
    private static final String ERROR_KEY = "GATEWAY_CLIENT_ENVIRONMENT_REJECTED";
    private static final String ERROR_MESSAGE = "客户端环境与当前网关环境不匹配";

    private final GatewayRequestGuardProperties properties;
    private final Environment environment;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public GatewayRequestGuardFilter(GatewayRequestGuardProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isGuardEnabled() || isOptions(request) || isIgnored(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        List<String> headerValues = readHeaderValues(request);
        if (headerValues.size() != 1 || !matchesExpectedValue(headerValues.get(0))) {
            reject(request, response, headerValues);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isGuardEnabled() {
        return isProductionProfile() || properties.isEnabled();
    }

    private boolean isProductionProfile() {
        return Arrays.stream(effectiveProfiles())
            .map(this::normalize)
            .anyMatch(MANDATORY_PRODUCTION_PROFILES::contains);
    }

    private String[] effectiveProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles != null && activeProfiles.length > 0) {
            return activeProfiles;
        }

        String configuredProfiles = environment.getProperty("spring.profiles.active", "");
        if (!StringUtils.hasText(configuredProfiles)) {
            return new String[0];
        }
        return configuredProfiles.split(",");
    }

    private List<String> readHeaderValues(HttpServletRequest request) {
        String headerName = resolvedHeaderName();
        Enumeration<String> headers = request.getHeaders(headerName);
        if (headers == null) {
            return List.of();
        }
        return Collections.list(headers);
    }

    private boolean matchesExpectedValue(String actualValue) {
        if (!StringUtils.hasText(actualValue)) {
            return false;
        }

        String normalizedActualValue = normalize(actualValue);
        return resolveExpectedValues().stream()
            .map(this::normalize)
            .filter(StringUtils::hasText)
            .anyMatch(normalizedActualValue::equals);
    }

    private List<String> resolveExpectedValues() {
        List<String> configuredValues = properties.getExpectedValue();
        if (configuredValues != null && configuredValues.stream().anyMatch(StringUtils::hasText)) {
            return configuredValues;
        }

        if (isProductionProfile()) {
            return List.of(PRODUCTION_ENVIRONMENT);
        }

        String inferredEnvironment = inferNonProductionEnvironment();
        return List.of(inferredEnvironment);
    }

    private String inferNonProductionEnvironment() {
        for (String profile : effectiveProfiles()) {
            String normalizedProfile = normalize(profile);
            if (Set.of("dev", "test", "staging").contains(normalizedProfile)) {
                return normalizedProfile;
            }
        }
        return DEFAULT_DEVELOPMENT_ENVIRONMENT;
    }

    private String resolvedHeaderName() {
        return StringUtils.hasText(properties.getHeaderName())
            ? properties.getHeaderName().trim()
            : "X-Frontend-Environment";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isOptions(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private boolean isIgnored(String requestUri) {
        List<String> ignoreUrls = properties.getIgnoreUrls();
        if (!StringUtils.hasText(requestUri) || ignoreUrls == null || ignoreUrls.isEmpty()) {
            return false;
        }
        return ignoreUrls.stream()
            .filter(StringUtils::hasText)
            .anyMatch(pattern -> antPathMatcher.match(pattern, requestUri));
    }

    private void reject(HttpServletRequest request,
                        HttpServletResponse response,
                        List<String> headerValues) throws IOException {
        Object traceId = request.getAttribute(GatewaySecurityAttributes.TRACE_ID);
        LOGGER.warn(
            "Gateway frontend environment rejected, traceId={}, uri={}, headerName={}, headerValueCount={}",
            traceId,
            request.getRequestURI(),
            resolvedHeaderName(),
            headerValues.size()
        );

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
            "{\"code\":403,\"errorKey\":\"" + ERROR_KEY + "\",\"msg\":\"" + ERROR_MESSAGE + "\",\"data\":null}"
        );
    }
}
