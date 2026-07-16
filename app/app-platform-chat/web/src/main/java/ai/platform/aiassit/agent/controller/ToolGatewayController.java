package ai.platform.aiassit.agent.controller;

import ai.platform.aiassit.agent.runtime.tool.ToolGatewayRequest;
import ai.platform.aiassit.agent.runtime.tool.ToolGatewayResponse;
import ai.platform.aiassit.agent.runtime.tool.ToolGatewayService;
import ai.platform.aiassit.service.ai.spi.tool.ToolInvocationPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.api.model.AuthorizationSnapshot;
import org.athena.framework.security.api.model.UserContext;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Authenticated runtime endpoint used by both Python and TypeScript Agent workers. */
@RestController
@RequestMapping("/api/v1/ai/tool-gateway")
public class ToolGatewayController {

    private final ToolGatewayService gatewayService;

    public ToolGatewayController(ToolGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @IgnoredResultWrapper
    @PostMapping("/{toolCode}/versions/{version}/invoke")
    public ToolGatewayResponse invoke(@PathVariable String toolCode,
                                      @PathVariable Integer version,
                                      @RequestBody ToolGatewayRequest request,
                                      @RequestHeader(value = "X-Tool-Approval", required = false) String approvalToken,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                      HttpServletRequest servletRequest) {
        UserContext current = SystemContext.getUserContext();
        if (current == null || current.subject() == null || current.subject().userId() == null) {
            throw BizException.of(ErrCodeConstant.LOGIN_FAILED);
        }
        AuthorizationSnapshot authorization = current.authorization();
        String traceId = firstText(servletRequest.getHeader("X-Trace-Id"),
                servletRequest.getHeader("traceId"), UUID.randomUUID().toString().replace("-", ""));
        ToolInvocationPrincipal principal = ToolInvocationPrincipal.builder()
                .userId(current.subject().userId())
                .roles(copy(authorization == null ? null : authorization.roles()))
                .permissions(copy(authorization == null ? null : authorization.permissions()))
                .traceId(traceId)
                .build();
        return gatewayService.invoke(toolCode, version, request, principal, approvalToken, idempotencyKey);
    }

    private Set<String> copy(Set<String> values) {
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }

    private String firstText(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) return value.trim();
            }
        }
        return null;
    }
}
