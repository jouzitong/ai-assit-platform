package ai.platform.aiassit.gateway.core.context;

/**
 * 网关安全请求属性 Key。
 */
public interface GatewaySecurityAttributes {

    String TOKEN = "gateway.security.token";

    String TOKEN_PARSE_STATUS = "gateway.security.token.parse.status";

    String USER_CONTEXT = "gateway.security.user.context";

    String TRACE_ID = "gateway.security.trace-id";

    String REQUIRED_PERMISSIONS = "gateway.security.required.permissions";

    String PERMISSION_RESPONSE = "gateway.security.permission.response";

    String PERMISSION_CHECK_STATUS = "gateway.security.permission.check.status";
}
