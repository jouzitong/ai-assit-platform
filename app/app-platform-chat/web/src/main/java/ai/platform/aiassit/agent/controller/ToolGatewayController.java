package ai.platform.aiassit.agent.controller;

import ai.platform.aiassit.agent.runtime.tool.ToolGatewayRequest;
import ai.platform.aiassit.agent.runtime.tool.ToolGatewayResponse;
import ai.platform.aiassit.agent.runtime.tool.ToolGatewayService;
import ai.platform.aiassit.service.ai.spi.tool.ToolInvocationPrincipal;
import ai.platform.aiassit.service.ai.spi.agent.AgentTemporaryTokenIssuer;
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

/**
 * Python 与 TypeScript Agent Worker 共用的已认证工具调用网关。
 *
 * <p>接口从当前登录态提取用户、角色和权限快照，签发短期执行令牌后再调用目标工具，确保工具执行具有可追溯的主体与幂等边界。</p>
 */
@RestController
@RequestMapping("/api/v1/ai/tool-gateway")
public class ToolGatewayController {

    private final ToolGatewayService gatewayService;
    private final AgentTemporaryTokenIssuer temporaryTokenIssuer;

    public ToolGatewayController(ToolGatewayService gatewayService,
                                 AgentTemporaryTokenIssuer temporaryTokenIssuer) {
        this.gatewayService = gatewayService;
        this.temporaryTokenIssuer = temporaryTokenIssuer;
    }

    /**
     * 以当前用户身份调用指定工具版本。
     *
     * @param toolCode       工具业务编码
     * @param version        工具版本号
     * @param request        工具调用请求体，包含符合工具契约的输入参数
     * @param approvalToken  可选审批令牌，供需要人工确认的工具校验
     * @param idempotencyKey 可选幂等键，避免重复提交产生重复副作用
     * @param servletRequest 原始 HTTP 请求，用于读取追踪标识
     * @return 未包装的工具调用响应，包含执行输出、状态和诊断信息
     */
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
                .executionToken(temporaryTokenIssuer.issue(current))
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
