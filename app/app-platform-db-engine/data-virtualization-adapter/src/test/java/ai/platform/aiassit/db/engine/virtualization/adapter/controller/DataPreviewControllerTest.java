package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.db.engine.api.DataPreviewApi;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryRequest;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryResponse;
import ai.platform.aiassit.db.engine.virtualization.adapter.service.DataPreviewApplicationService;
import org.athena.framework.web.vo.R;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DataPreviewControllerTest {

    @Test
    void apiContractUsesTheStableInternalRouteAndResultEnvelope() throws Exception {
        FeignClient feignClient = DataPreviewApi.class.getAnnotation(FeignClient.class);
        Method method = DataPreviewApi.class.getMethod("query", DataPreviewQueryRequest.class);

        assertThat(feignClient).isNotNull();
        assertThat(feignClient.name()).isEqualTo("dbEngine");
        assertThat(feignClient.contextId()).isEqualTo("platformDataPreviewClient");
        assertThat(feignClient.path()).isEqualTo("/dbEngine");
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/internal/v1/data-preview/query");
        assertThat(method.getReturnType()).isEqualTo(R.class);
    }

    @Test
    void controllerDelegatesAndWrapsTheResponse() {
        DataPreviewQueryRequest request = new DataPreviewQueryRequest();
        DataPreviewQueryResponse expected = new DataPreviewQueryResponse();
        AtomicReference<DataPreviewQueryRequest> capturedRequest = new AtomicReference<>();
        DataPreviewApplicationService service = new DataPreviewApplicationService(null, null, null) {
            @Override
            public DataPreviewQueryResponse query(DataPreviewQueryRequest source) {
                capturedRequest.set(source);
                return expected;
            }
        };
        DataPreviewController controller = new DataPreviewController(service);

        R<DataPreviewQueryResponse> actual = controller.query(request);

        assertThat(actual.isOk()).isTrue();
        assertThat(actual.getData()).isSameAs(expected);
        assertThat(capturedRequest.get()).isSameAs(request);
    }
}
