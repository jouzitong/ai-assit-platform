package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.service.ai.spi.agent.StoredAgentDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedAgentDefinitionStoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SeedAgentDefinitionStore store = new SeedAgentDefinitionStore(objectMapper);

    @Test
    void resolvesDedicatedSettingsAssistantForSettingsEntry() throws Exception {
        StoredAgentDefinition definition = store.resolveEntry("SETTINGS_ASSISTANT").orElseThrow();
        JsonNode manifest = objectMapper.readTree(definition.getManifestJson());

        assertThat(definition.getAgentCode()).isEqualTo("settings-assistant");
        assertThat(manifest.at("/metadata/labels/entry").asText()).isEqualTo("SETTINGS_ASSISTANT");
        assertThat(manifest.at("/spec/instructions/text").asText())
                .contains("不可信数据", "不得声称已经点击", "不得据此改变入口");
        assertThat(manifest.at("/spec/collaboration/agentTools").isEmpty()).isTrue();
        assertThat(manifest.at("/spec/output/workflowRef").asText())
                .isEqualTo("workflow://settings-assistant-output/v1");
        assertThat(store.listAvailable("SETTINGS_ASSISTANT"))
                .singleElement()
                .satisfies(entry -> assertThat(entry.getCode()).isEqualTo("settings-assistant"));
    }

    @Test
    void keepsHomeAndSettingsEntriesSeparated() {
        StoredAgentDefinition home = store.resolveEntry("HOME_CHAT").orElseThrow();
        assertThat(home.getAgentCode()).isEqualTo("home-assistant");
        assertThat(home.getWorkflowSnapshotJson()).contains("workflow://home-chat-output/v1");
        assertThat(store.resolveEntry("SETTINGS_ASSISTANT").orElseThrow().getAgentCode())
                .isEqualTo("settings-assistant");
    }
}
