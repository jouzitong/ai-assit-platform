package ai.platform.aiassit.gateway.core.filter;

import ai.platform.aiassit.gateway.core.config.GatewaySecurityProperties;
import ai.platform.aiassit.gateway.core.context.GatewaySecurityAttributes;
import ai.platform.aiassit.gateway.core.context.GatewayTokenContext;
import ai.platform.aiassit.gateway.core.service.GatewayTokenParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.athena.framework.security.api.spi.TokenParseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(-120)
public class GatewayTokenFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayTokenFilter.class);

    private final GatewaySecurityProperties properties;

    private final GatewayTokenParser tokenParser;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public GatewayTokenFilter(GatewaySecurityProperties properties, GatewayTokenParser tokenParser) {
        this.properties = properties;
        this.tokenParser = tokenParser;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled() || isOptions(request) || isIgnored(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader(properties.getHeaderName());
        GatewayTokenContext tokenContext = tokenParser.parseAuthorization(authorization, properties.getTokenPrefix());
        request.setAttribute(GatewaySecurityAttributes.TOKEN, tokenContext.token());
        request.setAttribute(GatewaySecurityAttributes.TOKEN_PARSE_STATUS, tokenContext.status());
        request.setAttribute(GatewaySecurityAttributes.USER_CONTEXT, tokenContext.userContext());

        if (!tokenContext.authenticated()) {
            LOGGER.debug("Gateway token rejected, uri={}, status={}", request.getRequestURI(), tokenContext.status());
            if (properties.isRequireToken()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setCharacterEncoding("UTF-8");
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Unauthorized");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isOptions(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private boolean isIgnored(String requestUri) {
        if (!StringUtils.hasText(requestUri) || properties.getIgnoreUrls() == null || properties.getIgnoreUrls().isEmpty()) {
            return false;
        }
        return properties.getIgnoreUrls().stream().anyMatch(pattern -> antPathMatcher.match(pattern, requestUri));
    }
}
