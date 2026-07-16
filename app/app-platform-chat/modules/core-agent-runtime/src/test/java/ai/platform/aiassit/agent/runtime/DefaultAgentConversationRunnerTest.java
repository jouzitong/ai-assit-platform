package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionStore;
import ai.platform.aiassit.service.ai.spi.agent.AgentEntrySummary;
import ai.platform.aiassit.service.ai.spi.agent.StoredAgentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAgentConversationRunnerTest {

    @Test
    void pinsAnUnversionedUserSelectionToTheAuthorizedEntryVersion() {
        DefaultAgentConversationRunner runner = runner(3);

        AgentTarget target = runner.authorizeTarget(AgentTarget.explicit("home-assistant", null), Map.of());

        assertThat(target.agentCode()).isEqualTo("home-assistant");
        assertThat(target.agentVersion()).isEqualTo(3);
    }

    @Test
    void rejectsAUserSelectedVersionThatIsNotBoundToTheEntry() {
        DefaultAgentConversationRunner runner = runner(3);

        assertThatThrownBy(() -> runner.authorizeTarget(
                AgentTarget.explicit("home-assistant", 4), Map.of()))
                .satisfies(error -> assertThat(error.toString()).contains("not available for HOME_CHAT"));
    }

    @Test
    void privilegedInternalCallerMayResolveTheCurrentPublishedVersion() {
        DefaultAgentConversationRunner runner = runner(3);
        AgentTarget requested = AgentTarget.explicit("internal-reviewer", null);

        AgentTarget target = runner.authorizeTarget(requested, Map.of("allowExplicitAgent", true));

        assertThat(target).isSameAs(requested);
    }

    private DefaultAgentConversationRunner runner(int entryVersion) {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentDefinitionStore store = new AgentDefinitionStore() {
            @Override
            public Optional<StoredAgentDefinition> resolve(String agentCode, Integer version) {
                return Optional.empty();
            }

            @Override
            public Optional<StoredAgentDefinition> resolveEntry(String entryCode) {
                return Optional.empty();
            }

            @Override
            public List<AgentEntrySummary> listAvailable(String entryCode) {
                return List.of(AgentEntrySummary.builder()
                        .code("home-assistant")
                        .name("Home")
                        .version(entryVersion)
                        .build());
            }
        };
        AgentSnapshotResolver resolver = new AgentSnapshotResolver(
                List.of(store), new AgentManifestValidator(), objectMapper);
        return new DefaultAgentConversationRunner(
                resolver,
                null,
                null,
                List.of(),
                List.of(),
                objectMapper,
                new AgentCapabilityGrantService());
    }
}
