package ai.platform.aiassit.service.ai.provider.client;

import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import ai.platform.aiassit.service.ai.provider.config.RagflowProperties;
import ai.platform.aiassit.service.ai.provider.dto.RagflowMemoryResponseMapper;
import ai.platform.aiassit.service.ai.spi.memory.MemoryProviderException;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryCreateRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryForgetRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryListRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryRecentRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemorySearchRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryStatusRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryWriteRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagflowMemoryClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean failWrites = new AtomicBoolean();
    private final AtomicBoolean listIncludesExtractedMessage = new AtomicBoolean();
    private final AtomicInteger contentLookups = new AtomicInteger();
    private HttpServer server;
    private RagflowMemoryClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/memories", this::handleMemories);
        server.createContext("/api/v1/messages", this::handleMessages);
        server.start();
        RagflowProperties properties = new RagflowProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("memory-test-key");
        properties.setTimeoutMs(2_000);
        client = new RagflowMemoryClient(
                properties, objectMapper, new RagflowMemoryResponseMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void createsMemoryWithMemorySpecificModelsAndConfiguration() {
        ProviderMemoryCreateRequest request = new ProviderMemoryCreateRequest();
        request.setMeta(new RequestMeta());
        request.setName("chat-session-owner-v1");
        request.setMemoryTypes(List.of(MemoryType.RAW, MemoryType.SEMANTIC, MemoryType.EPISODIC));
        request.setEmbeddingModel("memory-embedding-model");
        request.setExtractionModel("memory-extraction-model");
        request.setPermission("me");
        request.setMemorySize(1024);
        request.setForgettingPolicy("FIFO");

        var result = client.create(request);

        assertThat(result.getMemoryId()).isEqualTo("memory-session-1");
        assertThat(result.getMemoryTypes()).containsExactly(
                MemoryType.RAW, MemoryType.SEMANTIC, MemoryType.EPISODIC);
    }

    @Test
    void writesAndMapsListSearchAndRecentWithoutExposingEmbeddings() {
        ProviderMemoryWriteRequest write = new ProviderMemoryWriteRequest();
        write.setMeta(new RequestMeta());
        write.setMemoryIds(List.of("memory-session-1"));
        write.setAgentId("platform-chat");
        write.setSessionId("session-1");
        write.setUserId("platform-user-owner");
        write.setExternalId("round-idempotency-1");
        write.setUserInput("用户输入");
        write.setAgentResponse("助手回答");
        assertThat(client.addConversation(write).isAccepted()).isTrue();

        ProviderMemoryListRequest list = new ProviderMemoryListRequest();
        list.setMeta(new RequestMeta());
        list.setMemoryId("memory-session-1");
        list.setSessionId("session-1");
        assertThat(client.list(list).getItems())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getMessageId()).isEqualTo("message-1");
                    assertThat(item.getMemoryType()).isEqualTo(MemoryType.SEMANTIC);
                    assertThat(item.getContent()).isEqualTo("用户偏好表格展示");
                    assertThat(item.getUserId()).isEqualTo("platform-user-owner");
                    assertThat(item.getExternalId()).isEqualTo("round-idempotency-1");
                    assertThat(item.getProcessingStatus()).isEqualTo("COMPLETED");
                    assertThat(item).hasNoNullFieldsOrPropertiesExcept(
                            "similarity", "sourceId", "createdAt");
                });
        assertThat(contentLookups).hasValue(1);

        ProviderMemorySearchRequest search = new ProviderMemorySearchRequest();
        search.setMeta(new RequestMeta());
        search.setQuery("展示方式");
        search.setMemoryIds(List.of("memory-session-1"));
        search.setSessionId("session-1");
        search.setUserId("platform-user-owner");
        assertThat(client.search(search).getItems()).singleElement();

        ProviderMemoryRecentRequest recent = new ProviderMemoryRecentRequest();
        recent.setMeta(new RequestMeta());
        recent.setMemoryIds(List.of("memory-session-1"));
        recent.setSessionId("session-1");
        assertThat(client.recent(recent).getItems()).singleElement();
    }

    @Test
    void hydratesExtractedMemoryContentWhenPagedResponseOnlyContainsMetadata() {
        listIncludesExtractedMessage.set(true);
        ProviderMemoryListRequest list = new ProviderMemoryListRequest();
        list.setMeta(new RequestMeta());
        list.setMemoryId("memory-session-1");

        assertThat(client.list(list).getItems())
                .extracting(item -> item.getMemoryType(), item -> item.getContent())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MemoryType.RAW, null),
                        org.assertj.core.groups.Tuple.tuple(MemoryType.PROCEDURAL, "提炼出的工作方式"));
        assertThat(contentLookups).hasValue(1);
    }

    @Test
    void updatesStatusAndForgetsByEncodedProviderLocator() {
        ProviderMemoryStatusRequest status = new ProviderMemoryStatusRequest();
        status.setMeta(new RequestMeta());
        status.setMemoryId("memory/session:1");
        status.setMessageId("message/1:semantic");
        status.setEnabled(false);
        client.updateStatus(status);

        ProviderMemoryForgetRequest forget = new ProviderMemoryForgetRequest();
        forget.setMeta(new RequestMeta());
        forget.setMemoryId("memory/session:1");
        forget.setMessageId("message/1:semantic");
        client.forget(forget);
    }

    @Test
    void marksWriteFiveHundredOutcomeAsUncertain() {
        failWrites.set(true);
        ProviderMemoryWriteRequest write = new ProviderMemoryWriteRequest();
        write.setMeta(new RequestMeta());
        write.setMemoryIds(List.of("memory-session-1"));
        write.setAgentId("platform-chat");
        write.setSessionId("session-1");
        write.setUserId("platform-user-owner");
        write.setUserInput("用户输入");
        write.setAgentResponse("助手回答");

        assertThatThrownBy(() -> client.addConversation(write))
                .isInstanceOf(MemoryProviderException.class)
                .satisfies(error -> assertThat(((MemoryProviderException) error).isUncertain()).isTrue());
    }

    private void handleMemories(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer memory-test-key");
        if ("POST".equals(exchange.getRequestMethod())) {
            JsonNode body = objectMapper.readTree(exchange.getRequestBody());
            assertThat(body.path("embd_id").asText()).isEqualTo("memory-embedding-model");
            assertThat(body.path("llm_id").asText()).isEqualTo("memory-extraction-model");
            assertThat(body.path("memory_type").toString())
                    .isEqualTo("[\"raw\",\"semantic\",\"episodic\"]");
            respond(exchange, 200, "{\"code\":0,\"data\":{\"id\":\"memory-session-1\","
                    + "\"memory_type\":[\"raw\",\"semantic\",\"episodic\"]}}");
            return;
        }
        if ("GET".equals(exchange.getRequestMethod())) {
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/api/v1/memories/memory-session-1");
            respond(exchange, 200, listResponse());
            return;
        }
        respond(exchange, 405, "{\"code\":405}");
    }

    private void handleMessages(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer memory-test-key");
        String path = exchange.getRequestURI().getPath();
        if ("POST".equals(exchange.getRequestMethod())) {
            if (failWrites.get()) {
                respond(exchange, 500, "{\"code\":500}");
                return;
            }
            JsonNode body = objectMapper.readTree(exchange.getRequestBody());
            assertThat(body.path("memory_id").path(0).asText()).isEqualTo("memory-session-1");
            assertThat(body.path("user_id").asText()).isEqualTo("platform-user-owner");
            assertThat(body.path("external_id").asText()).isEqualTo("round-idempotency-1");
            respond(exchange, 200, "{\"code\":0,\"data\":null}");
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/v1/messages/search".equals(path)) {
            assertThat(exchange.getRequestURI().getRawQuery())
                    .contains("session_id=session-1", "user_id=platform-user-owner");
            respond(exchange, 200, messagesResponse());
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && path.endsWith("/content")) {
            contentLookups.incrementAndGet();
            if (path.endsWith("message-1/content")) {
                respond(exchange, 200, contentResponse("message-1", "semantic", "用户偏好表格展示"));
            } else if (path.endsWith("message-procedural/content")) {
                respond(exchange, 200, contentResponse(
                        "message-procedural", "procedural", "提炼出的工作方式"));
            } else {
                respond(exchange, 404, "{\"code\":404}");
            }
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/v1/messages".equals(path)) {
            assertThat(exchange.getRequestURI().getRawQuery()).contains("session_id=session-1");
            respond(exchange, 200, messagesResponse());
            return;
        }
        if ("PUT".equals(exchange.getRequestMethod())) {
            assertThat(exchange.getRequestURI().getRawPath())
                    .isEqualTo("/api/v1/messages/memory%2Fsession%3A1:message%2F1%3Asemantic");
            assertThat(objectMapper.readTree(exchange.getRequestBody()).path("status").asBoolean()).isFalse();
            respond(exchange, 200, "{\"code\":0}");
            return;
        }
        if ("DELETE".equals(exchange.getRequestMethod())) {
            assertThat(exchange.getRequestURI().getRawPath())
                    .isEqualTo("/api/v1/messages/memory%2Fsession%3A1:message%2F1%3Asemantic");
            respond(exchange, 200, "{\"code\":0}");
            return;
        }
        respond(exchange, 405, "{\"code\":405}");
    }

    private String messagesResponse() {
        return "{\"code\":0,\"data\":{\"total\":1,\"message_list\":[{"
                + "\"memory_id\":\"memory-session-1\",\"message_id\":\"message-1\","
                + "\"message_type\":\"semantic\",\"content\":\"用户偏好表格展示\","
                + "\"agent_id\":\"platform-chat\",\"session_id\":\"session-1\","
                + "\"user_id\":\"platform-user-owner\",\"external_id\":\"round-idempotency-1\",\"status\":true,"
                + "\"content_embed\":[0.1,0.2],\"task\":{\"progress\":1.0}}]}}";
    }

    private String listResponse() {
        if (listIncludesExtractedMessage.get()) {
            return "{\"code\":0,\"data\":{\"total\":1,\"message_list\":[{"
                    + "\"memory_id\":\"memory-session-1\",\"message_id\":\"message-raw\","
                    + "\"message_type\":\"raw\",\"agent_id\":\"platform-chat\","
                    + "\"session_id\":\"session-1\",\"user_id\":\"platform-user-owner\","
                    + "\"status\":true,\"task\":{\"progress\":1.0},\"extract\":[{"
                    + "\"memory_id\":\"memory-session-1\",\"message_id\":\"message-procedural\","
                    + "\"message_type\":\"procedural\",\"status\":true}]}]}}";
        }
        return "{\"code\":0,\"data\":{\"total\":1,\"message_list\":[{"
                + "\"memory_id\":\"memory-session-1\",\"message_id\":\"message-1\","
                + "\"message_type\":\"semantic\",\"agent_id\":\"platform-chat\","
                + "\"session_id\":\"session-1\",\"user_id\":\"platform-user-owner\","
                + "\"external_id\":\"round-idempotency-1\",\"status\":true,"
                + "\"task\":{\"progress\":1.0}}]}}";
    }

    private String contentResponse(String messageId, String messageType, String content) {
        return "{\"code\":0,\"data\":{\"memory_id\":\"memory-session-1\","
                + "\"message_id\":\"" + messageId + "\",\"message_type\":\""
                + messageType + "\",\"content\":\"" + content + "\","
                + "\"content_embed\":[0.1,0.2]}}";
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
