package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.agent.runtime.AgentConversationOutcome;
import ai.platform.aiassit.conversation.data.enums.ConversationArtifactType;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationArtifactDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.support.AgentConversationHistoryRecorder;
import ai.platform.aiassit.render.api.RenderInternalApi;
import ai.platform.aiassit.render.api.dto.RenderUpsertRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    void persistsValidatedRenderDocumentAndPublishesOnlyTheStoredReference() {
        AgentConversationHistoryRecorder historyRecorder = mock(AgentConversationHistoryRecorder.class);
        RenderInternalApi renderInternalApi = mock(RenderInternalApi.class);
        DefaultConversationExecutionServiceImpl service = new DefaultConversationExecutionServiceImpl(
                null,
                null,
                historyRecorder,
                null,
                null,
                null,
                null,
                renderInternalApi,
                new ObjectMapper()
        );
        List<ConversationQueryStreamEvent> events = new ArrayList<>();
        ConversationRuntimeContext context = runtimeContext("session-1", "round-1");
        context.setEventPublisher(events::add);

        ConversationArtifactDTO stored = new ConversationArtifactDTO();
        stored.setArtifactCode("artifact-render-1");
        stored.setArtifactType("RENDER_JSON");
        stored.setStage("FINAL");
        stored.setTitle("用户地址列表");
        stored.setContent("{\"pageCode\":\"user-addresses\",\"layout\":\"standard\"}");
        stored.setContentFormat("JSON");
        stored.setSeqNo(1);
        when(historyRecorder.saveArtifact(
                eq(context),
                eq(ConversationArtifactType.RENDER_JSON),
                eq("FINAL"),
                eq("用户地址列表"),
                any(),
                eq("JSON"),
                any()
        )).thenReturn(stored);

        AgentConversationOutcome outcome = new AgentConversationOutcome();
        outcome.setRunId("run-render-1");
        outcome.setRootAgentCode("dashboard-application-builder");
        outcome.setArtifacts(List.of(
                Map.of(
                        "artifactCode", "data-preview",
                        "artifactType", "JSON",
                        "content", Map.of("success", true)
                ),
                Map.of(
                        "artifactCode", "render-document",
                        "artifactType", "RENDER_JSON",
                        "contentFormat", "JSON",
                        "visible", true,
                        "status", "SUCCESS",
                        "title", "用户地址列表",
                        "content", Map.of(
                                "protocol", "render-json",
                                "protocolVersion", "1.0.0",
                                "pageId", "user-addresses",
                                "root", Map.of("component", "zg-common-list")
                        )
                ),
                Map.of(
                        "artifactCode", "validation-report",
                        "artifactType", "JSON",
                        "content", Map.of("valid", true)
                )
        ));

        service.persistArtifacts(context, outcome);

        ArgumentCaptor<RenderUpsertRequest> renderRequest = ArgumentCaptor.forClass(RenderUpsertRequest.class);
        verify(renderInternalApi).upsert(renderRequest.capture());
        String pageCode = renderRequest.getValue().getCode();
        assertThat(pageCode)
                .startsWith("chat-render-")
                .hasSize(64)
                .isNotEqualTo("user-addresses");
        assertThat(renderRequest.getValue().getContent())
                .containsEntry("protocol", "render-json")
                .containsKey("root");

        ArgumentCaptor<Object> storedContent = ArgumentCaptor.forClass(Object.class);
        verify(historyRecorder).saveArtifact(
                eq(context),
                eq(ConversationArtifactType.RENDER_JSON),
                eq("FINAL"),
                eq("用户地址列表"),
                storedContent.capture(),
                eq("JSON"),
                any()
        );
        assertThat(storedContent.getValue()).isInstanceOf(Map.class);
        Map<?, ?> renderReference = (Map<?, ?>) storedContent.getValue();
        assertThat(renderReference).hasSize(2);
        assertThat(renderReference.get("pageCode")).isEqualTo(pageCode);
        assertThat(renderReference.get("layout")).isEqualTo("standard");
        verifyNoMoreInteractions(renderInternalApi, historyRecorder);

        assertThat(events)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo("artifact.created");
                    assertThat(event.getStatus()).isEqualTo("SUCCESS");
                    assertThat(event.getExt())
                            .containsEntry("artifactCode", "artifact-render-1")
                            .containsEntry("artifactType", "RENDER_JSON")
                            .containsEntry("content", stored.getContent());
                });
    }

    @Test
    void skipsNonCanonicalRenderJsonWithoutUpsertingOrPersistingIt() {
        AgentConversationHistoryRecorder historyRecorder = mock(AgentConversationHistoryRecorder.class);
        RenderInternalApi renderInternalApi = mock(RenderInternalApi.class);
        DefaultConversationExecutionServiceImpl service = service(historyRecorder, renderInternalApi);
        AgentConversationOutcome outcome = new AgentConversationOutcome();
        outcome.setRunId("run-shadow");
        outcome.setArtifacts(List.of(Map.of(
                "artifactCode", "shadow-render",
                "artifactType", "RENDER_JSON",
                "content", renderDocument("user-addresses")
        )));

        service.persistArtifacts(runtimeContext("session-1", "round-1"), outcome);

        verifyNoInteractions(renderInternalApi, historyRecorder);
    }

    @Test
    void isolatesTheSameModelPageIdAcrossDifferentRuns() {
        AgentConversationHistoryRecorder historyRecorder = mock(AgentConversationHistoryRecorder.class);
        RenderInternalApi renderInternalApi = mock(RenderInternalApi.class);
        DefaultConversationExecutionServiceImpl service = service(historyRecorder, renderInternalApi);
        ConversationRuntimeContext context = runtimeContext("session-1", "round-1");

        service.persistArtifacts(context, renderOutcome("run-one", renderDocument("user-addresses")));
        service.persistArtifacts(context, renderOutcome("run-two", renderDocument("user-addresses")));

        ArgumentCaptor<RenderUpsertRequest> requests = ArgumentCaptor.forClass(RenderUpsertRequest.class);
        verify(renderInternalApi, times(2)).upsert(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(RenderUpsertRequest::getCode)
                .allSatisfy(code -> assertThat(code).startsWith("chat-render-").hasSize(64))
                .doesNotHaveDuplicates();
    }

    @Test
    void preservesExistingPageCodeForReferenceOnlyRenderArtifact() {
        AgentConversationHistoryRecorder historyRecorder = mock(AgentConversationHistoryRecorder.class);
        RenderInternalApi renderInternalApi = mock(RenderInternalApi.class);
        DefaultConversationExecutionServiceImpl service = service(historyRecorder, renderInternalApi);
        AgentConversationOutcome outcome = renderOutcome("run-reference", Map.of(
                "pageCode", "existing.render.page",
                "layout", "dashboard"
        ));

        service.persistArtifacts(runtimeContext("session-1", "round-1"), outcome);

        verifyNoInteractions(renderInternalApi);
        ArgumentCaptor<Object> storedContent = ArgumentCaptor.forClass(Object.class);
        verify(historyRecorder).saveArtifact(
                any(),
                eq(ConversationArtifactType.RENDER_JSON),
                eq("FINAL"),
                eq("render-document"),
                storedContent.capture(),
                eq("JSON"),
                any()
        );
        assertThat(storedContent.getValue()).isEqualTo(Map.of(
                "pageCode", "existing.render.page",
                "layout", "dashboard"
        ));
    }

    private DefaultConversationExecutionServiceImpl service(AgentConversationHistoryRecorder historyRecorder,
                                                            RenderInternalApi renderInternalApi) {
        return new DefaultConversationExecutionServiceImpl(
                null,
                null,
                historyRecorder,
                null,
                null,
                null,
                null,
                renderInternalApi,
                new ObjectMapper()
        );
    }

    private ConversationRuntimeContext runtimeContext(String sessionCode, String roundCode) {
        ConversationSessionDTO session = new ConversationSessionDTO();
        session.setSessionCode(sessionCode);
        ConversationRoundDTO round = new ConversationRoundDTO();
        round.setRoundCode(roundCode);
        ConversationRuntimeContext context = new ConversationRuntimeContext();
        context.setSession(session);
        context.setRound(round);
        return context;
    }

    private AgentConversationOutcome renderOutcome(String runId, Map<String, Object> content) {
        AgentConversationOutcome outcome = new AgentConversationOutcome();
        outcome.setRunId(runId);
        outcome.setRootAgentCode("dashboard-application-builder");
        outcome.setArtifacts(List.of(Map.of(
                "artifactCode", "render-document",
                "artifactType", "RENDER_JSON",
                "contentFormat", "JSON",
                "visible", true,
                "status", "SUCCESS",
                "content", content
        )));
        return outcome;
    }

    private Map<String, Object> renderDocument(String pageId) {
        return Map.of(
                "protocol", "render-json",
                "protocolVersion", "1.0.0",
                "pageId", pageId,
                "root", Map.of("component", "zg-common-list")
        );
    }
}
