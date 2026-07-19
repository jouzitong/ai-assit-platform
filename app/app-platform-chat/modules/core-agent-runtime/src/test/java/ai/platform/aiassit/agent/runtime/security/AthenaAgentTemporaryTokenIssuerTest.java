package ai.platform.aiassit.agent.runtime.security;

import ai.platform.aiassit.service.ai.spi.agent.AgentTemporaryTokenIssuer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.athena.framework.security.api.model.MutableUserContext;
import org.athena.framework.security.api.model.Subject;
import org.athena.framework.security.token.jwt.config.JwtTokenProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AthenaAgentTemporaryTokenIssuerTest {

    @Test
    void bindsNormalizedAgentRunIdAndIssuesTwoHourJwt() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setSecret("test-signing-secret");
        properties.setAccessTokenExpireMinutes(12_000);
        AthenaAgentTemporaryTokenIssuer issuer = new AthenaAgentTemporaryTokenIssuer(objectMapper, properties);
        MutableUserContext user = new MutableUserContext();
        user.setSubject(new Subject(7L, "tester", "default", "USER"));

        Map<String, Object> payload = decodePayload(objectMapper, issuer.issue(user, "  run-123  "));
        Map<String, Object> context = objectMapper.convertValue(payload.get("ctx"), new TypeReference<>() { });
        Map<String, Object> attributes = objectMapper.convertValue(
                context.get("attributes"), new TypeReference<>() { });

        assertThat(((Number) payload.get("exp")).longValue()
                - ((Number) payload.get("iat")).longValue()).isEqualTo(7_200L);
        assertThat(attributes)
                .containsEntry("credentialPurpose", "AI_AGENT_CHILD_PROCESS")
                .containsEntry("agentRunId", "run-123");
    }

    @Test
    void keepsTheLegacyIssueMethodCompatible() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AthenaAgentTemporaryTokenIssuer issuer = issuer(objectMapper);
        MutableUserContext user = user();

        Map<String, Object> payload = decodePayload(objectMapper, issuer.issue(user));
        Map<String, Object> context = objectMapper.convertValue(payload.get("ctx"), new TypeReference<>() { });
        Map<String, Object> attributes = objectMapper.convertValue(
                context.get("attributes"), new TypeReference<>() { });

        assertThat(attributes)
                .containsEntry("credentialPurpose", "AI_AGENT_CHILD_PROCESS")
                .doesNotContainKey("agentRunId");
    }

    @Test
    void defaultRunBoundMethodKeepsLegacyIssuersCompatible() {
        AgentTemporaryTokenIssuer legacyIssuer = userContext -> "legacy-token";

        assertThat(legacyIssuer.issue(user(), "run-123")).isEqualTo("legacy-token");
    }

    @Test
    void rejectsInvalidAgentRunIds() {
        AthenaAgentTemporaryTokenIssuer issuer = issuer(new ObjectMapper().findAndRegisterModules());
        MutableUserContext user = user();

        assertThatThrownBy(() -> issuer.issue(user, "r".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 64");
        assertThatThrownBy(() -> issuer.issue(user, "run-1\nforged"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control characters");
    }

    private AthenaAgentTemporaryTokenIssuer issuer(ObjectMapper objectMapper) {
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setSecret("test-signing-secret");
        properties.setAccessTokenExpireMinutes(12_000);
        return new AthenaAgentTemporaryTokenIssuer(objectMapper, properties);
    }

    private MutableUserContext user() {
        MutableUserContext user = new MutableUserContext();
        user.setSubject(new Subject(7L, "tester", "default", "USER"));
        return user;
    }

    private Map<String, Object> decodePayload(ObjectMapper objectMapper, String token) throws Exception {
        String[] chunks = token.split("\\.");
        return objectMapper.readValue(
                Base64.getUrlDecoder().decode(chunks[1]), new TypeReference<>() { });
    }
}
