package ai.platform.aiassit.render.core.controller;

import ai.platform.aiassit.render.api.RenderInternalApi;
import ai.platform.aiassit.render.api.dto.RenderDetailDTO;
import ai.platform.aiassit.render.api.dto.RenderGetRequest;
import ai.platform.aiassit.render.api.dto.RenderUpsertRequest;
import ai.platform.aiassit.render.core.service.RenderInternalApplicationService;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RenderInternalControllerTest {

    @Test
    void exposesInternalRenderRoutesAndDelegatesRequests() throws Exception {
        RenderInternalApplicationService service = mock(RenderInternalApplicationService.class);
        RenderInternalController controller = new RenderInternalController(service);
        RenderUpsertRequest upsertRequest = new RenderUpsertRequest();
        RenderGetRequest getRequest = new RenderGetRequest();
        RenderDetailDTO upserted = new RenderDetailDTO();
        RenderDetailDTO loaded = new RenderDetailDTO();
        when(service.upsert(upsertRequest)).thenReturn(upserted);
        when(service.get(getRequest)).thenReturn(loaded);

        assertThat(controller.upsert(upsertRequest)).isSameAs(upserted);
        assertThat(controller.get(getRequest)).isSameAs(loaded);
        verify(service).upsert(upsertRequest);
        verify(service).get(getRequest);

        RequestMapping classMapping = RenderInternalController.class
                .getAnnotation(RequestMapping.class);
        Method upsertMethod = RenderInternalController.class
                .getMethod("upsert", RenderUpsertRequest.class);
        Method getMethod = RenderInternalController.class
                .getMethod("get", RenderGetRequest.class);
        assertThat(classMapping.value()).containsExactly("/internal/v1/render");
        assertThat(upsertMethod.getAnnotation(PostMapping.class).value()).containsExactly("/upsert");
        assertThat(getMethod.getAnnotation(PostMapping.class).value()).containsExactly("/get");
        assertThat(upsertMethod.getAnnotation(IgnoredResultWrapper.class)).isNotNull();
        assertThat(getMethod.getAnnotation(IgnoredResultWrapper.class)).isNotNull();
        assertThat(upsertMethod.getParameters()[0].getAnnotation(RequestBody.class)).isNotNull();
        assertThat(getMethod.getParameters()[0].getAnnotation(RequestBody.class)).isNotNull();
        assertThat(RenderInternalApi.class).isAssignableFrom(RenderInternalController.class);
    }
}
