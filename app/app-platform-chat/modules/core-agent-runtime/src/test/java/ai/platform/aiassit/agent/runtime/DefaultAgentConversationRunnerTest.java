package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.model.service.AiModelConfigService;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionStore;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import ai.platform.aiassit.service.ai.spi.agent.AgentEntrySummary;
import ai.platform.aiassit.service.ai.spi.agent.StoredAgentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
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

    @Test
    void explicitModelSelectionTakesPriorityOverManifestModel() {
        AiModelConfigDTO selected = model(42L, "selected", "qwen-plus");
        AiModelConfigDTO manifest = model(43L, "manifest", "qwen-turbo");
        DefaultAgentConversationRunner runner = runner(3,
                modelService(Map.of(42L, selected), Map.of("manifest", manifest)));
        AgentConversationRequest request = new AgentConversationRequest();
        request.setModelId(42L);

        AiModelConfigDTO resolved = runner.resolveModel(request, snapshot("model://manifest"));

        assertThat(resolved).isSameAs(selected);
    }

    @Test
    void missingExplicitModelDoesNotSilentlyFallBackToManifestModel() {
        AiModelConfigDTO manifest = model(43L, "manifest", "qwen-turbo");
        DefaultAgentConversationRunner runner = runner(3,
                modelService(Map.of(), Map.of("manifest", manifest)));
        AgentConversationRequest request = new AgentConversationRequest();
        request.setModelId(99L);

        assertThatThrownBy(() -> runner.resolveModel(request, snapshot("model://manifest")))
                .satisfies(error -> assertThat(error.toString()).contains("99"));
    }

    @Test
    void disabledExplicitModelIsRejectedAtRuntimeBoundary() {
        AiModelConfigDTO disabled = model(42L, "disabled", "qwen-plus");
        disabled.setEnabled(false);
        DefaultAgentConversationRunner runner = runner(3,
                modelService(Map.of(42L, disabled), Map.of()));
        AgentConversationRequest request = new AgentConversationRequest();
        request.setModelId(42L);

        assertThatThrownBy(() -> runner.resolveModel(request, snapshot("model://default-quality")))
                .satisfies(error -> assertThat(error.toString()).contains("42"));
    }

    private DefaultAgentConversationRunner runner(int entryVersion) {
        return runner(entryVersion, null);
    }

    private DefaultAgentConversationRunner runner(int entryVersion, AiModelConfigService modelConfigService) {
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
                modelConfigService,
                null,
                List.of(),
                List.of(),
                objectMapper,
                new AgentCapabilityGrantService());
    }

    private AgentDefinitionSnapshot snapshot(String modelRef) {
        AgentDefinitionSnapshot snapshot = new AgentDefinitionSnapshot();
        snapshot.setRootAgent(Map.of("spec", Map.of("model", Map.of("ref", modelRef))));
        return snapshot;
    }

    private AiModelConfigDTO model(Long id, String modelCode, String apiModel) {
        AiModelConfigDTO model = new AiModelConfigDTO();
        model.setId(id);
        model.setModelCode(modelCode);
        model.setApiModel(apiModel);
        model.setBaseUrl("https://model.example/v1");
        model.setEnabled(true);
        return model;
    }

    private AiModelConfigService modelService(Map<Long, AiModelConfigDTO> byId,
                                              Map<String, AiModelConfigDTO> byCode) {
        return (AiModelConfigService) Proxy.newProxyInstance(
                AiModelConfigService.class.getClassLoader(),
                new Class<?>[]{AiModelConfigService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getResolvedById" -> byId.get(args[0]);
                    case "getByModelCode" -> byCode.get(args[0]);
                    case "selectEnabledModels" -> List.of();
                    case "toString" -> "AiModelConfigServiceStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
