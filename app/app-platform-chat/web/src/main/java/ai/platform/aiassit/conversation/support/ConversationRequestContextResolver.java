package ai.platform.aiassit.conversation.support;

import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;
import java.util.Set;

@Component
public class ConversationRequestContextResolver {

    public Long currentUserId() {
        UserContext userContext = SystemContext.getUserContext();
        if (userContext != null && userContext.subject() != null) {
            return userContext.subject().userId();
        }
        throw BizException.of(ErrCodeConstant.LOGIN_FAILED);
    }

    public String traceId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String traceId = attributes.getRequest().getHeader("traceId");
            if (traceId == null || traceId.isBlank()) {
                traceId = attributes.getRequest().getHeader("X-Trace-Id");
            }
            if (traceId != null && !traceId.isBlank()) {
                return traceId.trim();
            }
        }
        return newTraceId();
    }

    public String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Privileged protocol model overrides are limited to administrators or an explicit permission. */
    public boolean canOverrideModel() {
        UserContext userContext = SystemContext.getUserContext();
        if (userContext == null || userContext.authorization() == null) {
            return false;
        }
        Set<String> roles = userContext.authorization().roles();
        if (containsIgnoreCase(roles, "admin")) {
            return true;
        }
        Set<String> permissions = userContext.authorization().permissions();
        return containsIgnoreCase(permissions, "ai:chat:model-override")
                || containsIgnoreCase(permissions, "ai:agent:debug");
    }

    private boolean containsIgnoreCase(Set<String> values, String expected) {
        return values != null && values.stream()
                .anyMatch(value -> value != null && value.equalsIgnoreCase(expected));
    }
}
