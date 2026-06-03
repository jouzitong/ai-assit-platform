package ai.platform.aiassit.gateway.core.security;

import org.athena.framework.security.api.model.UserContext;
import org.athena.framework.security.api.spi.TokenManager;
import org.athena.framework.security.api.spi.TokenManagerWithParseResult;
import org.athena.framework.security.api.spi.TokenParseResult;
import org.athena.framework.security.api.spi.TokenParseStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GatewayTokenParser {

    private final TokenManager tokenManager;

    public GatewayTokenParser(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    public GatewayTokenContext parseAuthorization(String authorization, String tokenPrefix) {
        String token = extractToken(authorization, tokenPrefix);
        if (!StringUtils.hasText(token)) {
            return new GatewayTokenContext(null, TokenParseStatus.EMPTY, null);
        }
        return parseToken(token);
    }

    public GatewayTokenContext parseToken(String token) {
        if (!StringUtils.hasText(token)) {
            return new GatewayTokenContext(null, TokenParseStatus.EMPTY, null);
        }

        if (tokenManager instanceof TokenManagerWithParseResult tokenManagerWithParseResult) {
            TokenParseResult result = tokenManagerWithParseResult.parseWithResult(token);
            if (result == null) {
                return new GatewayTokenContext(token, TokenParseStatus.ERROR, null);
            }
            return new GatewayTokenContext(token, result.getStatus(), result.getUserContext());
        }

        try {
            UserContext userContext = tokenManager.parse(token);
            return new GatewayTokenContext(token, userContext == null ? TokenParseStatus.ERROR : TokenParseStatus.OK, userContext);
        } catch (Exception ex) {
            return new GatewayTokenContext(token, TokenParseStatus.ERROR, null);
        }
    }

    private String extractToken(String authorization, String tokenPrefix) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        if (!StringUtils.hasText(tokenPrefix)) {
            return authorization.trim();
        }
        String prefix = tokenPrefix.trim();
        if (!StringUtils.hasText(prefix)) {
            return authorization.trim();
        }
        if (authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return authorization.substring(prefix.length()).trim();
        }
        return authorization.trim();
    }
}
