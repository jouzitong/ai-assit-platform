package ai.platform.aiassit.chat.workflow.data.service.control;

import ai.platform.aiassit.chat.workflow.data.entity.AiAgentVersionEntity;
import ai.platform.aiassit.chat.workflow.data.enums.DefinitionStatus;
import ai.platform.aiassit.chat.workflow.data.support.ControlPlaneJsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEntryEligibilityPolicyTest {

    private final AgentEntryEligibilityPolicy policy = new AgentEntryEligibilityPolicy(
            new ControlPlaneJsonSupport(new ObjectMapper()));

    @Test
    void acceptsPublishedManifestWithMatchingStringEntryLabel() {
        AiAgentVersionEntity version = published("""
                {"metadata":{"labels":{"entry":"home_chat"}}}
                """);

        assertThat(policy.supports("HOME_CHAT", version)).isTrue();
    }

    @Test
    void toleratesLegacyRawManifestWithMatchingEntryCollection() {
        AiAgentVersionEntity version = published("""
                {"metadata":{"labels":{"entry":["WORKSPACE", "HOME_CHAT"]}}}
                """);

        assertThat(policy.supports("HOME_CHAT", version)).isTrue();
    }

    @Test
    void failsClosedForSpecialistDraftAndMalformedManifest() {
        AiAgentVersionEntity specialist = published("""
                {"metadata":{"labels":{"role":"specialist"}}}
                """);
        AiAgentVersionEntity draft = published("""
                {"metadata":{"labels":{"entry":"HOME_CHAT"}}}
                """);
        draft.setStatus(DefinitionStatus.DRAFT);
        AiAgentVersionEntity malformed = published("{not-json");

        assertThat(policy.supports("HOME_CHAT", specialist)).isFalse();
        assertThat(policy.supports("HOME_CHAT", draft)).isFalse();
        assertThat(policy.supports("HOME_CHAT", malformed)).isFalse();
    }

    private AiAgentVersionEntity published(String manifestJson) {
        AiAgentVersionEntity version = new AiAgentVersionEntity();
        version.setStatus(DefinitionStatus.PUBLISHED);
        version.setManifestJson(manifestJson);
        return version;
    }
}
