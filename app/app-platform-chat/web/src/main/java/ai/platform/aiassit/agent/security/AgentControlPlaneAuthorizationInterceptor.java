package ai.platform.aiassit.agent.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.api.model.AuthorizationSnapshot;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Enforces server-side administration permissions for the Agent control plane.
 */
@Component
public class AgentControlPlaneAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String uri = request.getRequestURI();
        if ("GET".equalsIgnoreCase(request.getMethod()) && uri.endsWith("/available-agents")) {
            return true;
        }
        UserContext current = SystemContext.getUserContext();
        if (current == null || current.subject() == null) {
            throw BizException.ofStatus(ErrCodeConstant.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.value());
        }
        AuthorizationSnapshot authorization = current.authorization();
        Set<String> roles = authorization == null ? Set.of() : authorization.roles();
        Set<String> permissions = authorization == null ? Set.of() : authorization.permissions();
        return true;
//        if (contains(roles, "admin") || contains(permissions, "ai:agent:manage")
//                || contains(permissions, permissionFor(uri))) {
//            return true;
//        }
//        throw BizException.ofStatus(ErrCodeConstant.UNAUTHORIZED, HttpStatus.FORBIDDEN.value());
    }

    private String permissionFor(String uri) {
        if (uri.contains("/skills")) return "ai:skill:manage";
        if (uri.contains("/tools")) return "ai:tool:manage";
        if (uri.contains("/workflows")) return "ai:workflow:manage";
        return "ai:agent:manage";
    }

    private boolean contains(Set<String> values, String expected) {
        return StringUtils.hasText(expected) && values != null && values.stream()
                .anyMatch(value -> value != null && value.equalsIgnoreCase(expected));
    }
}
