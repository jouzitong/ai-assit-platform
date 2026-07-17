package ai.platform.aiassit.agent.runtime.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.athena.framework.security.api.model.MutableUserContext;
import org.athena.framework.security.api.model.Subject;
import org.athena.framework.security.token.jwt.config.JwtTokenProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AthenaAgentTemporaryTokenIssuerTest {

    @Test
    void alwaysIssuesTwoHourJwtIndependentlyFromLoginTokenTtl() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setSecret("test-signing-secret");
        properties.setAccessTokenExpireMinutes(12_000);
        AthenaAgentTemporaryTokenIssuer issuer = new AthenaAgentTemporaryTokenIssuer(objectMapper, properties);
        MutableUserContext user = new MutableUserContext();
        user.setSubject(new Subject(7L, "tester", "default", "USER"));

        String token = issuer.issue(user);
        String[] chunks = token.split("\\.");
        Map<String, Object> payload = objectMapper.readValue(
                Base64.getUrlDecoder().decode(chunks[1]), new TypeReference<>() { });

        assertThat(((Number) payload.get("exp")).longValue()
                - ((Number) payload.get("iat")).longValue()).isEqualTo(7_200L);
    }
}
