package ai.platform.aiassit.render.data.component.controller;

import ai.platform.aiassit.render.api.RenderComponentCatalogInternalApi;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogQueryRequest;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogResponse;
import ai.platform.aiassit.render.data.component.service.RenderComponentCatalogApplicationService;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RenderComponentCatalogInternalControllerTest {

    @Test
    void apiContractUsesStableInternalCatalogRouteAndRawResult() throws Exception {
        FeignClient feignClient = RenderComponentCatalogInternalApi.class.getAnnotation(FeignClient.class);
        Method method = RenderComponentCatalogInternalApi.class.getMethod(
                "queryCatalog", RenderComponentCatalogQueryRequest.class);

        assertThat(feignClient).isNotNull();
        assertThat(feignClient.name()).isEqualTo("render");
        assertThat(feignClient.contextId()).isEqualTo("platformRenderComponentCatalogInternalClient");
        assertThat(feignClient.path()).isEqualTo("/render/internal/v1/render-components");
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly("/catalog/query");
        assertThat(method.isAnnotationPresent(IgnoredResultWrapper.class)).isTrue();
        assertThat(method.getParameters()[0].getAnnotation(RequestBody.class).required()).isFalse();
    }

    @Test
    void controllerExposesMatchingRouteAndDelegatesToApplicationService() throws Exception {
        AtomicReference<RenderComponentCatalogQueryRequest> capturedRequest = new AtomicReference<>();
        RenderComponentCatalogResponse expected = new RenderComponentCatalogResponse();
        RenderComponentCatalogApplicationService service = request -> {
            capturedRequest.set(request);
            return expected;
        };
        RenderComponentCatalogInternalController controller =
                new RenderComponentCatalogInternalController(service);
        RenderComponentCatalogQueryRequest request = new RenderComponentCatalogQueryRequest();

        RenderComponentCatalogResponse actual = controller.queryCatalog(request);

        RequestMapping classMapping = RenderComponentCatalogInternalController.class
                .getAnnotation(RequestMapping.class);
        Method method = RenderComponentCatalogInternalController.class.getMethod(
                "queryCatalog", RenderComponentCatalogQueryRequest.class);
        assertThat(classMapping.value()).containsExactly("/internal/v1/render-components");
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly("/catalog/query");
        assertThat(RenderComponentCatalogInternalApi.class)
                .isAssignableFrom(RenderComponentCatalogInternalController.class);
        assertThat(actual).isSameAs(expected);
        assertThat(capturedRequest.get()).isSameAs(request);
    }
}
