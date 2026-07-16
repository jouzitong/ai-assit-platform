package ai.platform.aiassit.agent.runtime.tool;

import ai.platform.aiassit.service.ai.spi.tool.PublishedToolDefinition;
import ai.platform.aiassit.service.ai.spi.tool.PublishedToolDefinitionStore;
import ai.platform.aiassit.service.ai.spi.tool.ToolInvocationPrincipal;
import ai.platform.aiassit.agent.runtime.AgentCapabilityGrantService;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.arthena.framework.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolGatewayServiceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void invokesAllowlistedPublishedHttpToolAfterPolicyAndSchemaChecks() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/lookup", exchange -> {
            byte[] body = "{\"value\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/lookup";
        ToolGatewayService service = service(definition(endpoint, "ai:data:read"));
        ToolGatewayRequest request = new ToolGatewayRequest();
        request.setArguments(Map.of("query", "orders"));
        request.setRun(Map.of("runId", "run-1", "snapshotHash", "sha256:test"));

        ToolGatewayResponse response = service.invoke("lookup", 3, request,
                principal(Set.of("ai:data:read")), null, "run-1:lookup:3");

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getOutput()).isEqualTo(Map.of("value", "ok"));
    }

    @Test
    void rejectsPermissionAndApprovalByDefault() {
        PublishedToolDefinition tool = definition("https://example.invalid/tool", "ai:data:read");
        ToolGatewayService service = service(tool);
        ToolGatewayRequest request = new ToolGatewayRequest();
        request.setArguments(Map.of("query", "orders"));
        request.setRun(Map.of("runId", "run-1", "snapshotHash", "sha256:test"));

        assertThatThrownBy(() -> service.invoke("lookup", 3, request,
                principal(Set.of()), null, "run-1"))
                .isInstanceOf(BizException.class);

        Map<String, Object> approvalDefinition = new java.util.LinkedHashMap<>(tool.getDefinition());
        approvalDefinition.put("permissionPolicy", Map.of());
        approvalDefinition.put("approvalPolicy", Map.of("required", true));
        ToolGatewayService approvalService = service(PublishedToolDefinition.builder()
                .toolCode("lookup").toolVersion(3).adapterType("HTTP")
                .checksum("hash").definition(approvalDefinition).build());
        assertThatThrownBy(() -> approvalService.invoke("lookup", 3, request,
                principal(Set.of()), "unverified", "run-1"))
                .isInstanceOf(BizException.class);
    }

    private ToolGatewayService service(PublishedToolDefinition definition) {
        PublishedToolDefinitionStore store = (code, version) ->
                "lookup".equals(code) && Integer.valueOf(3).equals(version)
                        ? Optional.of(definition) : Optional.empty();
        AgentCapabilityGrantService grants = new AgentCapabilityGrantService();
        AgentDefinitionSnapshot snapshot = new AgentDefinitionSnapshot();
        snapshot.setSnapshotHash("sha256:test");
        snapshot.setResolvedCapabilities(Map.of("tools", java.util.List.of(
                Map.of("code", "lookup", "version", 3))));
        grants.register("run-1", 7L, snapshot, java.time.Duration.ofMinutes(1));
        return new ToolGatewayService(store, java.util.List.of(), java.util.List.of(), new ObjectMapper(), grants);
    }

    private PublishedToolDefinition definition(String endpoint, String permission) {
        return PublishedToolDefinition.builder()
                .toolCode("lookup")
                .toolVersion(3)
                .adapterType("HTTP")
                .checksum("hash")
                .definition(Map.of(
                        "endpoint", endpoint,
                        "method", "POST",
                        "allowInsecureHttp", true,
                        "allowedHosts", java.util.List.of(endpoint.contains("127.0.0.1")
                                ? "127.0.0.1" : "example.invalid"),
                        "permissionPolicy", Map.of("requiredPermissions", java.util.List.of(permission)),
                        "inputSchema", Map.of(
                                "type", "object",
                                "required", java.util.List.of("query"),
                                "properties", Map.of("query", Map.of("type", "string")),
                                "additionalProperties", false),
                        "outputSchema", Map.of(
                                "type", "object",
                                "required", java.util.List.of("value"),
                                "properties", Map.of("value", Map.of("type", "string")))
                ))
                .build();
    }

    private ToolInvocationPrincipal principal(Set<String> permissions) {
        return ToolInvocationPrincipal.builder()
                .userId(7L)
                .roles(Set.of("user"))
                .permissions(permissions)
                .traceId("trace-1")
                .build();
    }
}
