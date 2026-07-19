package ai.platform.aiassit.agent.runtime.security;

import ai.platform.aiassit.service.ai.spi.agent.AgentTemporaryTokenIssuer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.athena.framework.security.api.model.MutableUserContext;
import org.athena.framework.security.api.model.SessionState;
import org.athena.framework.security.api.model.UserContext;
import org.athena.framework.security.token.jwt.config.JwtTokenProperties;
import org.athena.framework.security.token.jwt.service.JwtTokenManager;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Creates a JWT with the platform signing key and a fixed two-hour child-process lifetime. */
@Component
public class AthenaAgentTemporaryTokenIssuer implements AgentTemporaryTokenIssuer {

    static final Duration TOKEN_TTL = Duration.ofHours(2);
    static final int MAX_AGENT_RUN_ID_LENGTH = 64;

    private final JwtTokenManager tokenManager;

    public AthenaAgentTemporaryTokenIssuer(ObjectMapper objectMapper, JwtTokenProperties configuredProperties) {
        JwtTokenProperties temporaryProperties = new JwtTokenProperties();
        temporaryProperties.setEnabled(configuredProperties.isEnabled());
        temporaryProperties.setSecret(configuredProperties.getSecret());
        temporaryProperties.setAccessTokenExpireMinutes(TOKEN_TTL.toMinutes());
        this.tokenManager = new JwtTokenManager(objectMapper, temporaryProperties);
    }

    @Override
    public String issue(UserContext source) {
        return issue(source, null);
    }

    @Override
    public String issue(UserContext source, String agentRunId) {
        if (source == null || source.subject() == null) {
            throw new IllegalArgumentException("Authenticated user context is required");
        }
        String normalizedAgentRunId = normalizeAgentRunId(agentRunId);
        Instant issuedAt = Instant.now();
        MutableUserContext temporary = new MutableUserContext();
        temporary.setSubject(source.subject());
        temporary.setAuthn(source.authn());
        temporary.setAuthorization(source.authorization());
        temporary.setSession(new SessionState(
                "agent-" + UUID.randomUUID(),
                "agent-" + UUID.randomUUID(),
                issuedAt,
                issuedAt.plus(TOKEN_TTL)));
        if (source.attributes() != null) {
            temporary.getAttributes().putAll(source.attributes());
        }
        temporary.getAttributes().put("credentialPurpose", "AI_AGENT_CHILD_PROCESS");
        if (normalizedAgentRunId != null) {
            temporary.getAttributes().put("agentRunId", normalizedAgentRunId);
        }
        return tokenManager.create(temporary);
    }

    private String normalizeAgentRunId(String agentRunId) {
        if (agentRunId == null) {
            return null;
        }
        if (agentRunId.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Agent run id must not contain control characters");
        }
        String normalized = agentRunId.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_AGENT_RUN_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Agent run id must not exceed " + MAX_AGENT_RUN_ID_LENGTH + " characters");
        }
        return normalized;
    }
}
