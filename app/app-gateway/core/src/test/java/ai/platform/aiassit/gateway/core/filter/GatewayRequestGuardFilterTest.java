package ai.platform.aiassit.gateway.core.filter;

import ai.platform.aiassit.gateway.core.config.GatewayRequestGuardProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRequestGuardFilterTest {

    @Test
    void productionProfileCannotDisableTheGuard() throws Exception {
        GatewayRequestGuardProperties properties = properties(false, List.of("prod"));
        GatewayRequestGuardFilter filter = filter(properties, "prod");
        MockHttpServletRequest request = request("/chat/api/v1/test");
        request.addHeader("X-Frontend-Environment", "test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void nonProductionProfileIsDisabledByDefault() throws Exception {
        GatewayRequestGuardProperties properties = new GatewayRequestGuardProperties();
        GatewayRequestGuardFilter filter = filter(properties, "test");
        MockHttpServletRequest request = request("/chat/api/v1/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void nonProductionProfileCanBeEnabledExplicitly() throws Exception {
        GatewayRequestGuardProperties properties = properties(true, List.of("test"));
        GatewayRequestGuardFilter filter = filter(properties, "test");
        MockHttpServletRequest request = request("/chat/api/v1/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void expectedValuesAreComparedIgnoringCaseAndWhitespace() throws Exception {
        GatewayRequestGuardProperties properties = properties(true, List.of(" DEV ", "Prod"));
        GatewayRequestGuardFilter filter = filter(properties, "test");
        MockHttpServletRequest request = request("/chat/api/v1/test");
        request.addHeader("X-Frontend-Environment", " prod ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void productionProfileDefaultsToProdWhenExpectedValuesAreEmpty() throws Exception {
        GatewayRequestGuardProperties properties = properties(false, List.of());
        GatewayRequestGuardFilter filter = filter(properties, "PROD");
        MockHttpServletRequest request = request("/chat/api/v1/test");
        request.addHeader("X-Frontend-Environment", "PrOd");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void preflightRequestIsHandledBeforeEnvironmentValidation() throws Exception {
        GatewayRequestGuardProperties properties = properties(true, List.of("prod"));
        GatewayRequestGuardFilter filter = filter(properties, "prod");
        MockHttpServletRequest request = request("/chat/api/v1/test");
        request.setMethod("OPTIONS");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    private GatewayRequestGuardProperties properties(boolean enabled, List<String> expectedValues) {
        GatewayRequestGuardProperties properties = new GatewayRequestGuardProperties();
        properties.setEnabled(enabled);
        properties.setExpectedValue(expectedValues);
        return properties;
    }

    private GatewayRequestGuardFilter filter(GatewayRequestGuardProperties properties, String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return new GatewayRequestGuardFilter(properties, environment);
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        request.setMethod("GET");
        return request;
    }
}
