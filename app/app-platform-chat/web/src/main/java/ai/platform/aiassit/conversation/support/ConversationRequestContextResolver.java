package ai.platform.aiassit.conversation.support;

import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

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
}
