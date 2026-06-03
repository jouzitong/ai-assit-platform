package ai.platform.aiassit.gateway.core.filter;

import ai.platform.aiassit.gateway.core.config.GatewaySecurityProperties;
import ai.platform.aiassit.gateway.core.context.GatewayPermissionSnapshot;
import ai.platform.aiassit.gateway.core.context.GatewaySecurityAttributes;
import ai.platform.aiassit.gateway.core.service.GatewayPermissionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.athena.framework.security.api.model.UserContext;
import org.athena.framework.security.api.spi.TokenParseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(-90)
public class GatewayPermissionFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayPermissionFilter.class);

    private final GatewaySecurityProperties properties;

    private final GatewayPermissionService permissionService;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public GatewayPermissionFilter(GatewaySecurityProperties properties, GatewayPermissionService permissionService) {
        this.properties = properties;
        this.permissionService = permissionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled() || !properties.getPermission().isEnabled() || isOptions(request) || isIgnored(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        Object tokenStatus = request.getAttribute(GatewaySecurityAttributes.TOKEN_PARSE_STATUS);
        if (tokenStatus != TokenParseStatus.OK) {
            filterChain.doFilter(request, response);
            return;
        }

        UserContext userContext = (UserContext) request.getAttribute(GatewaySecurityAttributes.USER_CONTEXT);
        if (userContext == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Set<String> requiredPermissions = readRequiredPermissions(request);
        GatewayPermissionSnapshot snapshot = permissionService.queryPermission(
            userContext,
            properties.getPermission().getAppCode(),
            requiredPermissions
        );
        request.setAttribute(GatewaySecurityAttributes.PERMISSION_RESPONSE, snapshot.response());
        request.setAttribute(GatewaySecurityAttributes.PERMISSION_CHECK_STATUS, snapshot.granted());

        if (!snapshot.granted()) {
            LOGGER.debug("Gateway permission rejected, uri={}, requiredPermissions={}",
                request.getRequestURI(), requiredPermissions);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Forbidden");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isOptions(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private Set<String> readRequiredPermissions(HttpServletRequest request) {
        Object requiredPermissionsAttribute = request.getAttribute(GatewaySecurityAttributes.REQUIRED_PERMISSIONS);
        if (requiredPermissionsAttribute instanceof Set<?> set) {
            return set.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        }

        String requiredPermissionsHeader = request.getHeader(properties.getPermission().getRequiredPermissionsHeader());
        return permissionService.parseRequiredPermissions(requiredPermissionsHeader);
    }

    private boolean isIgnored(String requestUri) {
        if (!StringUtils.hasText(requestUri) || properties.getIgnoreUrls() == null || properties.getIgnoreUrls().isEmpty()) {
            return false;
        }
        return properties.getIgnoreUrls().stream().anyMatch(pattern -> antPathMatcher.match(pattern, requestUri));
    }
}
