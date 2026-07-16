package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCapabilityGrantServiceTest {

    @Test
    void grantIsBoundToRunUserSnapshotCapabilityAndVersion() {
        AgentCapabilityGrantService service = new AgentCapabilityGrantService();
        AgentDefinitionSnapshot snapshot = snapshot();

        service.register("run-1", 7L, snapshot, Duration.ofMinutes(1));

        assertThat(service.allows("run-1", 7L, "sha256:test", "tool", "kb-search", 2)).isTrue();
        assertThat(service.allows("run-1", 7L, "sha256:test", "skill", "review-policy", 4)).isTrue();
        assertThat(service.allows("run-1", 8L, "sha256:test", "tool", "kb-search", 2)).isFalse();
        assertThat(service.allows("run-1", 7L, "sha256:other", "tool", "kb-search", 2)).isFalse();
        assertThat(service.allows("run-1", 7L, "sha256:test", "tool", "kb-search", 3)).isFalse();
        assertThat(service.allows("run-1", 7L, "sha256:test", "tool", "not-granted", 1)).isFalse();
    }

    @Test
    void revokeInvalidatesGrantImmediately() {
        AgentCapabilityGrantService service = new AgentCapabilityGrantService();
        service.register("run-1", 7L, snapshot(), Duration.ofMinutes(1));

        service.revoke("run-1");

        assertThat(service.allows("run-1", 7L, "sha256:test", "tool", "kb-search", 2)).isFalse();
    }

    private AgentDefinitionSnapshot snapshot() {
        AgentDefinitionSnapshot snapshot = new AgentDefinitionSnapshot();
        snapshot.setSnapshotHash("sha256:test");
        snapshot.setResolvedCapabilities(Map.of(
                "tools", List.of(Map.of("code", "kb-search", "version", 2)),
                "skills", List.of(Map.of("code", "review-policy", "version", 4))));
        return snapshot;
    }
}
