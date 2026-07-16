package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionStore;
import ai.platform.aiassit.service.ai.spi.agent.AgentEntrySummary;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeType;
import ai.platform.aiassit.service.ai.spi.agent.StoredAgentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSnapshotResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolvesPinnedCollaboratorGraphAndStableHash() throws Exception {
        InMemoryStore store = new InMemoryStore();
        store.add(definition("manager", 3, List.of(ref("reviewer", 2, "review_result"))));
        store.add(definition("reviewer", 2, List.of()));
        store.entry = "manager";
        AgentSnapshotResolver resolver = resolver(store);

        var first = resolver.resolve(AgentTarget.homeChat());
        var second = resolver.resolve(AgentTarget.homeChat());

        assertThat(first.getAgentCode()).isEqualTo("manager");
        assertThat(first.getAgentVersion()).isEqualTo(3);
        assertThat(first.getAgentGraph())
                .singleElement()
                .extracting(item -> ((Map<?, ?>) item.get("metadata")).get("version"))
                .isEqualTo(2);
        assertThat(first.getSnapshotHash())
                .startsWith("sha256:")
                .isEqualTo(second.getSnapshotHash());
    }

    @Test
    void rejectsCyclesBeforeInvokingRuntime() throws Exception {
        InMemoryStore store = new InMemoryStore();
        store.add(definition("one", 1, List.of(ref("two", 1, "call_two"))));
        store.add(definition("two", 1, List.of(ref("one", 1, "call_one"))));
        store.entry = "one";

        assertThatThrownBy(() -> resolver(store).resolve(AgentTarget.homeChat()))
                .satisfies(error -> assertThat(error.toString()).contains("cycle detected"));
    }

    @Test
    void mergesCapabilitiesFromRootAndCollaboratorSnapshots() throws Exception {
        InMemoryStore store = new InMemoryStore();
        store.add(definition(
                "manager",
                3,
                List.of(ref("reviewer", 2, "review_result")),
                Map.of("tools", List.of(Map.of("code", "search", "version", 1)))));
        store.add(definition(
                "reviewer",
                2,
                List.of(),
                Map.of("skills", List.of(Map.of("code", "review-policy", "version", 4)))));
        store.entry = "manager";

        var snapshot = resolver(store).resolve(AgentTarget.homeChat());

        assertThat((List<?>) snapshot.getResolvedCapabilities().get("tools"))
                .singleElement()
                .satisfies(item -> {
                    Map<?, ?> capability = (Map<?, ?>) item;
                    assertThat(capability.get("code")).isEqualTo("search");
                    assertThat(capability.get("version")).isEqualTo(1);
                });
        assertThat((List<?>) snapshot.getResolvedCapabilities().get("skills"))
                .singleElement()
                .satisfies(item -> {
                    Map<?, ?> capability = (Map<?, ?>) item;
                    assertThat(capability.get("code")).isEqualTo("review-policy");
                    assertThat(capability.get("version")).isEqualTo(4);
                });
    }

    private AgentSnapshotResolver resolver(AgentDefinitionStore store) {
        return new AgentSnapshotResolver(List.of(store), new AgentManifestValidator(), objectMapper);
    }

    private StoredAgentDefinition definition(String code,
                                             int version,
                                             List<Map<String, Object>> collaborators) throws Exception {
        return definition(code, version, collaborators, Map.of());
    }

    private StoredAgentDefinition definition(String code,
                                             int version,
                                             List<Map<String, Object>> collaborators,
                                             Map<String, Object> capabilities) throws Exception {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("instructions", Map.of("type", "inline", "text", "Execute " + code));
        spec.put("model", Map.of("ref", "model://default-quality"));
        spec.put("collaboration", Map.of("agentTools", collaborators, "handoffs", List.of()));
        Map<String, Object> manifest = Map.of(
                "apiVersion", "ai.platform/v1alpha1",
                "kind", "Agent",
                "metadata", Map.of("code", code, "name", code, "version", version),
                "spec", spec
        );
        return StoredAgentDefinition.builder()
                .agentCode(code)
                .agentVersion(version)
                .name(code)
                .manifestJson(objectMapper.writeValueAsString(manifest))
                .runtimeType(AgentRuntimeType.OPENAI_AGENTS_PYTHON)
                .sdkVersion("0.8.1")
                .resolvedCapabilitiesJson(objectMapper.writeValueAsString(capabilities))
                .workflowSnapshotJson("{}")
                .build();
    }

    private Map<String, Object> ref(String code, int version, String toolName) {
        return Map.of(
                "targetAgentRef", "agent://" + code + "/v" + version,
                "toolName", toolName
        );
    }

    private static final class InMemoryStore implements AgentDefinitionStore {
        private final Map<String, StoredAgentDefinition> values = new LinkedHashMap<>();
        private String entry;

        private void add(StoredAgentDefinition value) {
            values.put(value.getAgentCode() + ":" + value.getAgentVersion(), value);
        }

        @Override
        public Optional<StoredAgentDefinition> resolve(String agentCode, Integer version) {
            if (version != null) {
                return Optional.ofNullable(values.get(agentCode + ":" + version));
            }
            return values.values().stream()
                    .filter(value -> value.getAgentCode().equals(agentCode))
                    .max(java.util.Comparator.comparing(StoredAgentDefinition::getAgentVersion));
        }

        @Override
        public Optional<StoredAgentDefinition> resolveEntry(String entryCode) {
            return resolve(entry, null);
        }

        @Override
        public List<AgentEntrySummary> listAvailable(String entryCode) {
            return List.of();
        }
    }
}
