package ai.platform.aiassit.agent.controller;

import ai.platform.aiassit.agent.runtime.tool.AiAgentPlatformToolFacadeService;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryRequest;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryResponse;
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

        assertThat(feignClient.name()).isEqualTo("chat");
        assertThat(feignClient.path()).isEqualTo("/chat");
        assertThat(previewMethod.getAnnotation(PostMapping.class).value())
                .containsExactly("/internal/v1/ai/agent-tools/data-preview/query");
        assertOptionalTransportInputs(previewMethod);
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

        private RecordingFacadeService() {
            super(request -> null);
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
    }
}
