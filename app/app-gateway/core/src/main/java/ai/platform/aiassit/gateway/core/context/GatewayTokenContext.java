package ai.platform.aiassit.gateway.core.context;

import org.athena.framework.security.api.model.UserContext;
import org.athena.framework.security.api.spi.TokenParseStatus;

/**
 * 网关 token 解析结果。
 */
public record GatewayTokenContext(String token, TokenParseStatus status, UserContext userContext) {

    public boolean authenticated() {
        return status == TokenParseStatus.OK && userContext != null;
    }
}
