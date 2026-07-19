package ai.platform.aiassit.render.data.component.service.impl;

import ai.platform.aiassit.render.api.dto.RenderComponentCatalogQueryRequest;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogResponse;
import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import ai.platform.aiassit.render.data.component.entity.dto.RenderComponentContentDTO;
import ai.platform.aiassit.render.data.component.entity.dto.RenderComponentDTO;
import ai.platform.aiassit.render.data.component.service.RenderComponentContentService;
import ai.platform.aiassit.render.data.component.service.RenderComponentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class RenderComponentCatalogApplicationServiceImplTest {

    @Test
    void returnsOnlyPublishedComponentsAndExtractsVersionFromComponentAssetV1() {
        List<RenderComponentDTO> components = List.of(
                component("table.basic", "Basic table", "data", EffectiveStatus.PUBLISHED,
                        LocalDateTime.of(2026, 7, 18, 10, 0)),
                component("chart.line", "Line chart", "chart", EffectiveStatus.PUBLISHED,
                        LocalDateTime.of(2026, 7, 18, 9, 0)),
                component("chart.draft", "Draft chart", "chart", EffectiveStatus.DRAFT,
                        LocalDateTime.of(2026, 7, 18, 11, 0))
        );
        List<RenderComponentContentDTO> contents = List.of(
                content("chart.line", "Revenue trend", """
                        {
                          "schemaVersion": "component-asset/v1",
                          "sourceComponent": {"version": " 2.1.0 "}
                        }
                        """, LocalDateTime.of(2026, 7, 18, 12, 0)),
                content("table.basic", "Order details", """
                        {
                          "schemaVersion": "component-asset/v0",
                          "sourceComponent": {"version": "9.9.9"}
                        }
                        """, LocalDateTime.of(2026, 7, 18, 8, 0))
        );

        RenderComponentCatalogResponse response = service(() -> components, () -> contents).query(null);

        assertThat(response.getComponents())
                .extracting(component -> component.getComponentKey())
                .containsExactly("chart.line", "table.basic");
        assertThat(response.getComponents().get(0).getComponentVersion()).isEqualTo("2.1.0");
        assertThat(response.getComponents().get(0).getUpdatedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 18, 12, 0));
        assertThat(response.getComponents().get(1).getComponentVersion()).isNull();
        assertThat(response.getComponents())
                .allSatisfy(component -> assertThat(component.getSourceRevision())
                        .matches("sha256:[0-9a-f]{64}"));
        assertThat(response.getCatalogRevision()).matches("sha256:[0-9a-f]{64}");
    }

    @Test
    void filtersByKeysCategoryAndKeywordWithoutChangingFullCatalogRevision() {
        List<RenderComponentDTO> components = List.of(
                component("chart.line", "Line chart", "chart", EffectiveStatus.PUBLISHED, null),
                component("table.basic", "Basic table", "data", EffectiveStatus.PUBLISHED, null),
                component("metric.card", "Metric card", "kpi", EffectiveStatus.PUBLISHED, null)
        );
        List<RenderComponentContentDTO> contents = List.of(
                content("chart.line", "Monthly revenue trend", "{}", null),
                content("table.basic", "Order details", "{}", null),
                content("metric.card", "Headline metric", "{}", null)
        );
        RenderComponentCatalogApplicationServiceImpl service = service(() -> components, () -> contents);
        String fullCatalogRevision = service.query(null).getCatalogRevision();

        RenderComponentCatalogQueryRequest request = new RenderComponentCatalogQueryRequest();
        request.setComponentKeys(List.of(" chart.line ", "table.basic"));
        request.setCategory("CHART");
        request.setKeyword("REVENUE");
        request.setLimit(10);

        RenderComponentCatalogResponse response = service.query(request);

        assertThat(response.getComponents())
                .extracting(component -> component.getComponentKey())
                .containsExactly("chart.line");
        assertThat(response.getCatalogRevision()).isEqualTo(fullCatalogRevision);
    }

    @Test
    void clampsRequestedLimitToOneHundred() {
        List<RenderComponentDTO> components = new ArrayList<>();
        for (int index = 0; index < 105; index++) {
            components.add(component("component.%03d".formatted(index), "Component " + index,
                    "test", EffectiveStatus.PUBLISHED, null));
        }
        RenderComponentCatalogQueryRequest request = new RenderComponentCatalogQueryRequest();
        request.setLimit(1_000);

        RenderComponentCatalogResponse response = service(() -> components, List::of).query(request);

        assertThat(response.getComponents()).hasSize(100);
        assertThat(response.getComponents().get(0).getComponentKey()).isEqualTo("component.000");
        assertThat(response.getComponents().get(99).getComponentKey()).isEqualTo("component.099");
    }

    @Test
    void revisionsChangeWhenPublishedComponentContentChanges() {
        List<RenderComponentDTO> components = List.of(
                component("chart.line", "Line chart", "chart", EffectiveStatus.PUBLISHED, null)
        );
        AtomicReference<List<RenderComponentContentDTO>> contents = new AtomicReference<>(List.of(
                content("chart.line", "Initial documentation", "{}", null)
        ));
        RenderComponentCatalogApplicationServiceImpl service = service(() -> components, contents::get);

        RenderComponentCatalogResponse before = service.query(null);
        contents.set(List.of(content("chart.line", "Updated documentation", "{}", null)));
        RenderComponentCatalogResponse after = service.query(null);

        assertThat(after.getComponents().get(0).getSourceRevision())
                .isNotEqualTo(before.getComponents().get(0).getSourceRevision());
        assertThat(after.getCatalogRevision()).isNotEqualTo(before.getCatalogRevision());
    }

    private RenderComponentCatalogApplicationServiceImpl service(
            Supplier<List<RenderComponentDTO>> components,
            Supplier<List<RenderComponentContentDTO>> contents) {
        RenderComponentService componentService = queryAllProxy(RenderComponentService.class, components);
        RenderComponentContentService contentService = queryAllProxy(RenderComponentContentService.class, contents);
        return new RenderComponentCatalogApplicationServiceImpl(componentService, contentService, new ObjectMapper());
    }

    private <T, R> T queryAllProxy(Class<T> serviceType, Supplier<List<R>> values) {
        return serviceType.cast(Proxy.newProxyInstance(
                serviceType.getClassLoader(),
                new Class<?>[]{serviceType},
                (proxy, method, args) -> {
                    if ("queryAll".equals(method.getName())) {
                        return values.get();
                    }
                    if ("toString".equals(method.getName())) {
                        return serviceType.getSimpleName() + "TestProxy";
                    }
                    throw new UnsupportedOperationException("Unexpected service call: " + method.getName());
                }
        ));
    }

    private RenderComponentDTO component(String key,
                                         String name,
                                         String category,
                                         EffectiveStatus status,
                                         LocalDateTime updateTime) {
        RenderComponentDTO component = new RenderComponentDTO();
        component.setKey(key);
        component.setName(name);
        component.setCategory(category);
        component.setStatus(status);
        component.setUpdateTime(updateTime);
        return component;
    }

    private RenderComponentContentDTO content(String componentKey,
                                              String docMarkdown,
                                              String exampleJson,
                                              LocalDateTime updateTime) {
        RenderComponentContentDTO content = new RenderComponentContentDTO();
        content.setComponentKey(componentKey);
        content.setDocMarkdown(docMarkdown);
        content.setExampleJson(exampleJson);
        content.setUpdateTime(updateTime);
        return content;
    }
}
