package ai.platform.aiassit.agent.runtime.tool;

import ai.platform.aiassit.db.engine.api.DataPreviewApi;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryRequest;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryResponse;
import ai.platform.aiassit.render.api.RenderComponentCatalogInternalApi;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogComponentDTO;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogQueryRequest;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogResponse;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.api.model.MutableUserContext;
import org.athena.framework.security.api.model.Subject;
import org.athena.framework.web.vo.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAgentPlatformToolFacadeServiceTest {

    private RecordingDataPreviewApi dataPreviewApi;
    private RecordingRenderComponentCatalogApi renderComponentCatalogApi;
    private AiAgentPlatformToolFacadeService service;
    private MutableUserContext userContext;

    @BeforeEach
    void setUp() {
        dataPreviewApi = new RecordingDataPreviewApi();
        renderComponentCatalogApi = new RecordingRenderComponentCatalogApi();
        service = new AiAgentPlatformToolFacadeService(dataPreviewApi, renderComponentCatalogApi);

        userContext = new MutableUserContext();
        userContext.setSubject(new Subject(7L, "agent-user", "default", "USER"));
        userContext.getAttributes().put(
                "credentialPurpose",
                AiAgentPlatformToolFacadeService.AGENT_CREDENTIAL_PURPOSE
        );
        userContext.getAttributes().put("agentRunId", "run-1");
        SystemContext.setUserContext(userContext);
    }

    @AfterEach
    void tearDown() {
        SystemContext.clearUserContext();
    }

    @Test
    void delegatesAValidDataPreviewRequestAndReturnsTheCheckedResponse() {
        DataPreviewQueryRequest request = previewRequest();
        DataPreviewQueryResponse response = previewResponse();
        dataPreviewApi.response = R.ok(response);

        DataPreviewQueryResponse actual = service.queryDataPreview("run-1", "trace-1", request);

        assertThat(actual).isSameAs(response);
        assertThat(dataPreviewApi.request).isSameAs(request);
        assertThat(dataPreviewApi.invocationCount).isEqualTo(1);
        assertThat(renderComponentCatalogApi.invocationCount).isZero();
    }

    @Test
    void rejectsRequestsThatDoNotUseAnAgentChildProcessCredential() {
        userContext.getAttributes().remove("credentialPurpose");

        assertThatThrownBy(() -> service.queryDataPreview("run-1", "trace-1", previewRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(AiChatBizCodeConstant.TOOL_PERMISSION_DENIED);
                    assertThat(exception.getStatus()).isEqualTo(403);
                });

        assertNoDownstreamInteractions();
    }

    @Test
    void rejectsUnauthenticatedRequestsBeforeCallingDownstreamServices() {
        SystemContext.clearUserContext();

        assertThatThrownBy(() -> service.queryDataPreview("run-1", "trace-1", previewRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(AiChatBizCodeConstant.TOOL_PERMISSION_DENIED);
                    assertThat(exception.getStatus()).isEqualTo(401);
                });

        assertNoDownstreamInteractions();
    }

    @Test
    void rejectsAMissingRunIdInsideTheAuditedFacadeBoundary() {
        assertThatThrownBy(() -> service.queryDataPreview(null, "trace-1", previewRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(AiChatBizCodeConstant.INVALID_TOOL_INPUT));

        assertNoDownstreamInteractions();
    }

    @Test
    void rejectsARunIdThatDoesNotMatchTheTokenBinding() {
        userContext.getAttributes().put("agentRunId", "run-from-token");

        assertThatThrownBy(() -> service.queryDataPreview("different-run", null, previewRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(AiChatBizCodeConstant.TOOL_PERMISSION_DENIED);
                    assertThat(exception.getStatus()).isEqualTo(403);
                });

        assertNoDownstreamInteractions();
    }

    @Test
    void rejectsAChildCredentialWithoutARunBinding() {
        userContext.getAttributes().remove("agentRunId");

        assertThatThrownBy(() -> service.queryDataPreview("run-1", null, previewRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(AiChatBizCodeConstant.TOOL_PERMISSION_DENIED);
                    assertThat(exception.getStatus()).isEqualTo(403);
                });

        assertNoDownstreamInteractions();
    }

    @Test
    void rejectsInvalidInputBeforeCallingDbEngine() {
        DataPreviewQueryRequest request = previewRequest();
        request.setLimit(101);

        assertThatThrownBy(() -> service.queryDataPreview("run-1", null, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(AiChatBizCodeConstant.INVALID_TOOL_INPUT));

        assertNoDownstreamInteractions();
    }

    @Test
    void failsClosedWhenDbEngineReturnsAMismatchedRawDto() {
        DataPreviewQueryRequest request = previewRequest();
        DataPreviewQueryResponse response = previewResponse();
        response.setModel("another_model");
        dataPreviewApi.response = R.ok(response);

        assertThatThrownBy(() -> service.queryDataPreview("run-1", null, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED));
    }

    @Test
    void failsClosedWhenDbEngineReturnsAFailureEnvelope() {
        dataPreviewApi.response = new R<DataPreviewQueryResponse>().setCode(500);

        assertThatThrownBy(() -> service.queryDataPreview("run-1", null, previewRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED));
    }

    @Test
    void delegatesAValidRenderCatalogRequestAndReturnsTheCheckedResponse() {
        RenderComponentCatalogQueryRequest request = new RenderComponentCatalogQueryRequest();
        request.setComponentKeys(List.of("chart.line"));
        request.setLimit(10);
        RenderComponentCatalogResponse response = renderCatalogResponse();
        renderComponentCatalogApi.response = R.ok(response);

        RenderComponentCatalogResponse actual = service.queryRenderComponentCatalog(
                "run-1",
                "trace-1",
                request
        );

        assertThat(actual).isSameAs(response);
        assertThat(renderComponentCatalogApi.request).isSameAs(request);
        assertThat(renderComponentCatalogApi.invocationCount).isEqualTo(1);
        assertThat(dataPreviewApi.invocationCount).isZero();
    }

    @Test
    void acceptsAValidCatalogEntryWhoseOptionalContractContentIsUnavailable() {
        RenderComponentCatalogQueryRequest request = new RenderComponentCatalogQueryRequest();
        request.setComponentKeys(List.of("chart.line"));
        RenderComponentCatalogResponse response = renderCatalogResponse();
        response.getComponents().get(0).setComponentVersion(null);
        response.getComponents().get(0).setDocMarkdown(null);
        response.getComponents().get(0).setExampleJson(null);
        renderComponentCatalogApi.response = R.ok(response);

        assertThat(service.queryRenderComponentCatalog("run-1", null, request)).isSameAs(response);
    }

    @Test
    void failsClosedWhenRenderReturnsAnInvalidSourceRevision() {
        RenderComponentCatalogQueryRequest request = new RenderComponentCatalogQueryRequest();
        request.setComponentKeys(List.of("chart.line"));
        RenderComponentCatalogResponse response = renderCatalogResponse();
        response.getComponents().get(0).setSourceRevision("not-a-revision");
        renderComponentCatalogApi.response = R.ok(response);

        assertThatThrownBy(() -> service.queryRenderComponentCatalog("run-1", null, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED));
    }

    private void assertNoDownstreamInteractions() {
        assertThat(dataPreviewApi.invocationCount).isZero();
        assertThat(renderComponentCatalogApi.invocationCount).isZero();
    }

    private DataPreviewQueryRequest previewRequest() {
        DataPreviewQueryRequest request = new DataPreviewQueryRequest();
        request.setModel("sales_order");
        request.setSourceRevision("virtual-model/v1");
        request.setCatalogVersion(1L);
        request.setLimit(20);
        DataPreviewQueryRequest.Dimension dimension = new DataPreviewQueryRequest.Dimension();
        dimension.setField("region");
        request.setDimensions(List.of(dimension));
        return request;
    }

    private DataPreviewQueryResponse previewResponse() {
        DataPreviewQueryResponse response = new DataPreviewQueryResponse();
        response.setModel("sales_order");
        response.setSourceRevision("virtual-model/v1");
        response.setCatalogVersion(1L);
        response.setQueryType("LIST");
        DataPreviewQueryResponse.Column column = new DataPreviewQueryResponse.Column();
        column.setKey("region");
        column.setField("region");
        response.setColumns(List.of(column));
        response.setRecords(List.of(Map.of("region", "APAC")));
        response.setTotal(1L);
        response.setTruncated(false);
        return response;
    }

    private RenderComponentCatalogResponse renderCatalogResponse() {
        RenderComponentCatalogComponentDTO component = new RenderComponentCatalogComponentDTO();
        component.setComponentKey("chart.line");
        component.setName("Line Chart");
        component.setCategory("chart");
        component.setComponentVersion("1.0.0");
        component.setSourceRevision("sha256:" + "b".repeat(64));
        component.setDocMarkdown("## 4. 参数契约\n|参数|类型|必填|使用|默认值|说明|\n|---|---|---|---|---|---|");
        component.setExampleJson("{\"schemaVersion\":\"component-asset/v1\"}");

        RenderComponentCatalogResponse response = new RenderComponentCatalogResponse();
        response.setCatalogRevision("sha256:" + "a".repeat(64));
        response.setComponents(List.of(component));
        return response;
    }

    private static final class RecordingDataPreviewApi implements DataPreviewApi {

        private DataPreviewQueryRequest request;
        private R<DataPreviewQueryResponse> response;
        private int invocationCount;

        @Override
        public R<DataPreviewQueryResponse> query(DataPreviewQueryRequest request) {
            this.request = request;
            invocationCount++;
            return response;
        }
    }

    private static final class RecordingRenderComponentCatalogApi
            implements RenderComponentCatalogInternalApi {

        private RenderComponentCatalogQueryRequest request;
        private R<RenderComponentCatalogResponse> response;
        private int invocationCount;

        @Override
        public R<RenderComponentCatalogResponse> queryCatalog(RenderComponentCatalogQueryRequest request) {
            this.request = request;
            invocationCount++;
            return response;
        }
    }
}
