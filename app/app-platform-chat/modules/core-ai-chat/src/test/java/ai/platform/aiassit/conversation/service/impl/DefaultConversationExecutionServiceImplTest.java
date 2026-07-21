package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.conversation.data.enums.ConversationArtifactType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultConversationExecutionServiceImplTest {

    @Test
    void onlyRecognizesFileImageAndRenderJsonArtifacts() {
        assertThat(DefaultConversationExecutionServiceImpl.supportedArtifactType(
                Map.of("artifactType", "FILE"))).isEqualTo(ConversationArtifactType.FILE);
        assertThat(DefaultConversationExecutionServiceImpl.supportedArtifactType(
                Map.of("artifactType", "image"))).isEqualTo(ConversationArtifactType.IMAGE);
        assertThat(DefaultConversationExecutionServiceImpl.supportedArtifactType(
                Map.of("artifactType", "RENDER_JSON"))).isEqualTo(ConversationArtifactType.RENDER_JSON);
        assertThat(DefaultConversationExecutionServiceImpl.supportedArtifactType(
                Map.of("artifactType", "TEXT"))).isNull();
    }

    @Test
    void onlyPersistsVisibleSuccessfulArtifacts() {
        assertThat(DefaultConversationExecutionServiceImpl.shouldPersistArtifact(
                Map.of("visible", true, "status", "SUCCESS"))).isTrue();
        assertThat(DefaultConversationExecutionServiceImpl.shouldPersistArtifact(
                Map.of("visible", false, "status", "SUCCESS"))).isFalse();
        assertThat(DefaultConversationExecutionServiceImpl.shouldPersistArtifact(
                Map.of("visible", true, "status", "FAILED"))).isFalse();
    }

    @Test
    void distinguishesMinimalRenderReferenceFromRenderDocument() {
        assertThat(DefaultConversationExecutionServiceImpl.isRenderReference(
                Map.of("pageCode", "complete.page-code", "layout", "standard"))).isTrue();
        assertThat(DefaultConversationExecutionServiceImpl.isRenderReference(
                Map.of("pageCode", "complete.page-code"))).isTrue();
        assertThat(DefaultConversationExecutionServiceImpl.isRenderReference(
                Map.of("pageCode", "complete.page-code", "root", Map.of("component", "text")))).isFalse();
    }
}
