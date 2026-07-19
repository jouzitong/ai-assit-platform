package ai.platform.aiassit.agent.controller;

import ai.platform.aiassit.agent.runtime.tool.AiAgentPlatformToolFacadeService;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryRequest;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryResponse;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogQueryRequest;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogResponse;
import ai.platform.aiassit.service.ai.api.AiAgentPlatformToolInternalApi;
import org.athena.framework.web.vo.R;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AiAgentPlatformToolInternalControllerTest {

    private final RecordingFacadeService facadeService = new RecordingFacadeService();
    private final AiAgentPlatformToolInternalController controller =
            new AiAgentPlatformToolInternalController(facadeService);

    @Test
    void apiContractUsesChatOwnedInternalRoutesAndOptionalTransportInputs() throws Exception {
        FeignClient feignClient = AiAgentPlatformToolInternalApi.class.getAnnotation(FeignClient.class);
        Method previewMethod = AiAgentPlatformToolInternalApi.class.getMethod(
                "queryDataPreview",
                String.class,
                String.class,
                DataPreviewQueryRequest.class
        );
        Method catalogMethod = AiAgentPlatformToolInternalApi.class.getMethod(
                "queryRenderComponentCatalog",
                String.class,
                String.class,
                RenderComponentCatalogQueryRequest.class
        );

        assertThat(feignClient.name()).isEqualTo("chat");
        assertThat(feignClient.path()).isEqualTo("/chat");
        assertThat(previewMethod.getAnnotation(PostMapping.class).value())
                .containsExactly("/internal/v1/ai/agent-tools/data-preview/query");
        assertThat(catalogMethod.getAnnotation(PostMapping.class).value())
                .containsExactly("/internal/v1/ai/agent-tools/render-components/catalog/query");
        assertOptionalTransportInputs(previewMethod);
        assertOptionalTransportInputs(catalogMethod);
    }

    @Test
    void wrapsTheCheckedDataPreviewResult() {
        DataPreviewQueryRequest request = new DataPreviewQueryRequest();
        DataPreviewQueryResponse response = new DataPreviewQueryResponse();
        facadeService.dataPreviewResponse = response;

        R<DataPreviewQueryResponse> actual = controller.queryDataPreview("run-1", "trace-1", request);

        assertThat(actual.getCode()).isZero();
        assertThat(actual.getData()).isSameAs(response);
        assertThat(facadeService.runId).isEqualTo("run-1");
        assertThat(facadeService.traceId).isEqualTo("trace-1");
        assertThat(facadeService.dataPreviewRequest).isSameAs(request);
    }

    @Test
    void wrapsTheCheckedRenderCatalogResult() {
        RenderComponentCatalogQueryRequest request = new RenderComponentCatalogQueryRequest();
        RenderComponentCatalogResponse response = new RenderComponentCatalogResponse();
        facadeService.renderCatalogResponse = response;

        R<RenderComponentCatalogResponse> actual =
                controller.queryRenderComponentCatalog("run-1", null, request);

        assertThat(actual.getCode()).isZero();
        assertThat(actual.getData()).isSameAs(response);
        assertThat(facadeService.runId).isEqualTo("run-1");
        assertThat(facadeService.traceId).isNull();
        assertThat(facadeService.renderCatalogRequest).isSameAs(request);
    }

    private void assertOptionalTransportInputs(Method method) {
        assertThat(method.getParameters()[0].getAnnotation(RequestHeader.class).required()).isFalse();
        assertThat(method.getParameters()[1].getAnnotation(RequestHeader.class).required()).isFalse();
        assertThat(method.getParameters()[2].getAnnotation(RequestBody.class).required()).isFalse();
    }

    private static final class RecordingFacadeService extends AiAgentPlatformToolFacadeService {

        private String runId;
        private String traceId;
        private DataPreviewQueryRequest dataPreviewRequest;
        private DataPreviewQueryResponse dataPreviewResponse;
        private RenderComponentCatalogQueryRequest renderCatalogRequest;
        private RenderComponentCatalogResponse renderCatalogResponse;

        private RecordingFacadeService() {
            super(request -> null, request -> null);
        }

        @Override
        public DataPreviewQueryResponse queryDataPreview(String runId,
                                                         String traceId,
                                                         DataPreviewQueryRequest request) {
            this.runId = runId;
            this.traceId = traceId;
            dataPreviewRequest = request;
            return dataPreviewResponse;
        }

        @Override
        public RenderComponentCatalogResponse queryRenderComponentCatalog(
                String runId,
                String traceId,
                RenderComponentCatalogQueryRequest request
        ) {
            this.runId = runId;
            this.traceId = traceId;
            renderCatalogRequest = request;
            return renderCatalogResponse;
        }
    }
}
