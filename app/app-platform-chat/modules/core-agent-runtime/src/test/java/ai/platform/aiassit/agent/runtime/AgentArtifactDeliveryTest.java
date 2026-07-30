package ai.platform.aiassit.agent.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentArtifactDeliveryTest {

    @Test
    void validatedRenderBuilderRouteRequiresRenderDocumentDelivery() {
        assertThat(AgentArtifactDelivery.forValidatedRoute(
                true, "dashboard-application-builder"))
                .isEqualTo(AgentArtifactDelivery.RENDER_DOCUMENT);
    }

    @Test
    void targetSelectionOrUnvalidatedRoutesKeepStandardDelivery() {
        AgentConversationRequest explicitlyTargeted = new AgentConversationRequest();
        explicitlyTargeted.setTarget(AgentTarget.explicit("dashboard-application-builder", 1));

        assertThat(explicitlyTargeted.getArtifactDelivery())
                .isEqualTo(AgentArtifactDelivery.STANDARD);
        assertThat(AgentArtifactDelivery.forValidatedRoute(
                false, "dashboard-application-builder"))
                .isEqualTo(AgentArtifactDelivery.STANDARD);
        assertThat(AgentArtifactDelivery.forValidatedRoute(true, "data-analysis"))
                .isEqualTo(AgentArtifactDelivery.STANDARD);
        assertThat(AgentArtifactDelivery.forValidatedRoute(true, null))
                .isEqualTo(AgentArtifactDelivery.STANDARD);
    }
}
