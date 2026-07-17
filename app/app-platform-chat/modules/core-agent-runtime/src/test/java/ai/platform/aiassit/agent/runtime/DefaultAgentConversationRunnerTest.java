package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.model.service.AiModelConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAgentConversationRunnerTest {

    @Test
    void explicitModelSelectionIsResolvedWithoutAnAgentManifest() {
        AiModelConfigDTO selected = model(42L, "selected", "qwen-plus");
        DefaultAgentConversationRunner runner = runner(modelService(Map.of(42L, selected)));
        AgentConversationRequest request = new AgentConversationRequest();
        request.setModelId(42L);

        assertThat(runner.resolveModel(request)).isSameAs(selected);
    }

    @Test
    void missingExplicitModelIsRejected() {
        DefaultAgentConversationRunner runner = runner(modelService(Map.of()));
        AgentConversationRequest request = new AgentConversationRequest();
        request.setModelId(99L);

        assertThatThrownBy(() -> runner.resolveModel(request))
                .satisfies(error -> assertThat(error.toString()).contains("99"));
    }

    @Test
    void disabledExplicitModelIsRejectedAtRuntimeBoundary() {
        AiModelConfigDTO disabled = model(42L, "disabled", "qwen-plus");
        disabled.setEnabled(false);
        DefaultAgentConversationRunner runner = runner(modelService(Map.of(42L, disabled)));
        AgentConversationRequest request = new AgentConversationRequest();
        request.setModelId(42L);

        assertThatThrownBy(() -> runner.resolveModel(request))
                .satisfies(error -> assertThat(error.toString()).contains("42"));
    }

    private DefaultAgentConversationRunner runner(AiModelConfigService modelConfigService) {
        return new DefaultAgentConversationRunner(
                modelConfigService,
                null,
                List.of(),
                List.of(),
                new ObjectMapper());
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

    private AiModelConfigService modelService(Map<Long, AiModelConfigDTO> byId) {
        return (AiModelConfigService) Proxy.newProxyInstance(
                AiModelConfigService.class.getClassLoader(),
                new Class<?>[]{AiModelConfigService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getResolvedById" -> byId.get(args[0]);
                    case "selectEnabledModels" -> List.of();
                    case "toString" -> "AiModelConfigServiceStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
