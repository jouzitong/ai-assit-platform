package ai.platform.aiassit.conversation.service.impl;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultConversationExecutionServiceImplTest {

    @Test
    void hidesDuplicateFinalAnswerArtifactsIdentifiedByCodeOrLegacyTitle() {
        assertThat(DefaultConversationExecutionServiceImpl.persistedArtifactVisible(
                Map.of("artifactCode", "final-answer", "visible", true),
                "  Same answer\n",
                "Same answer\r\n"
        )).isFalse();
        assertThat(DefaultConversationExecutionServiceImpl.persistedArtifactVisible(
                Map.of("title", "final_answer", "visible", true),
                "Same answer",
                "Same answer"
        )).isFalse();
    }

    @Test
    void preservesRequestedVisibilityForOrdinaryOrNonDuplicateArtifacts() {
        assertThat(DefaultConversationExecutionServiceImpl.persistedArtifactVisible(
                Map.of("artifactCode", "render-document", "visible", true),
                "Same answer",
                "Same answer"
        )).isTrue();
        assertThat(DefaultConversationExecutionServiceImpl.persistedArtifactVisible(
                Map.of("artifactCode", "final-answer", "visible", true),
                "A distinct audited result",
                "Same answer"
        )).isTrue();
        assertThat(DefaultConversationExecutionServiceImpl.persistedArtifactVisible(
                Map.of("artifactCode", "data-export", "visible", false),
                "Hidden by producer",
                "Same answer"
        )).isFalse();
    }
}
