package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.conversation.data.enums.ConversationArtifactType;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationArtifactDTO;
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

    @Test
    void buildsAuthoritativeArtifactEventPayloadFromPersistedRecord() {
        ConversationArtifactDTO artifact = new ConversationArtifactDTO();
        artifact.setArtifactCode("artifact-1");
        artifact.setArtifactType("RENDER_JSON");
        artifact.setStage("FINAL");
        artifact.setTitle("系统配置");
        artifact.setContent("{\"pageCode\":\"complete-code\",\"layout\":\"standard\"}");
        artifact.setContentFormat("JSON");
        artifact.setSeqNo(1);
        artifact.setExtJson("{\"agentCode\":\"render-agent\"}");

        assertThat(DefaultConversationExecutionServiceImpl.artifactEventPayload(artifact))
                .containsEntry("artifactCode", "artifact-1")
                .containsEntry("artifactType", "RENDER_JSON")
                .containsEntry("stage", "FINAL")
                .containsEntry("title", "系统配置")
                .containsEntry("content", "{\"pageCode\":\"complete-code\",\"layout\":\"standard\"}")
                .containsEntry("contentFormat", "JSON")
                .containsEntry("seqNo", 1);
    }
}
