package ai.platform.aiassit.service.ai.provider.client;

import ai.platform.aiassit.service.ai.api.dto.KbDocument;
import ai.platform.aiassit.service.ai.api.dto.KbSearchItem;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.provider.config.RagflowProperties;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagflowKnowledgeBaseClientTest {

    private HttpServer server;
    private RagflowKnowledgeBaseClient client;
    private final AtomicInteger createCalls = new AtomicInteger();
    private final AtomicInteger chunkCalls = new AtomicInteger();
    private final AtomicInteger deleteCalls = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/datasets/dataset-1/documents", this::handleDocuments);
        server.createContext("/api/v1/datasets/dataset-1/documents/doc-1/chunks", this::handleChunks);
        server.createContext("/api/v1/retrieval", this::handleRetrieval);
        server.start();

        RagflowProperties properties = new RagflowProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("test-key");
        properties.setTimeoutMs(3000);
        client = new RagflowKnowledgeBaseClient(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldWriteSearchAndDeleteKnowledgeDocuments() throws Exception {
        KbDocument document = new KbDocument();
        document.setDocumentId("local-doc-1");
        document.setContent("销售额 = 已支付订单金额 - 退款金额");
        document.getMetadata().put("documentName", "metric.md");

        RagflowKnowledgeBaseClient.UpsertResult result = client.upsert("dataset-1", List.of(document), new RequestMeta());

        assertEquals(1, result.accepted());
        assertEquals("doc-1", result.documentIdMappings().get("local-doc-1"));
        assertEquals(1, createCalls.get());
        assertEquals(1, chunkCalls.get());

        List<KbSearchItem> items = client.search("dataset-1", "销售额怎么算？", 5, new RequestMeta());

        assertEquals(1, items.size());
        assertEquals("doc-1", items.get(0).getDocumentId());
        assertEquals(0.93D, items.get(0).getScore());
        assertEquals("sales", items.get(0).getMetadata().get("business"));

        assertEquals(1, client.deleteDocuments("dataset-1", List.of("doc-1"), new RequestMeta()));
        assertEquals(1, deleteCalls.get());
    }

    private void handleDocuments(HttpExchange exchange) throws IOException {
        assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
        if ("POST".equals(exchange.getRequestMethod())) {
            createCalls.incrementAndGet();
            assertTrue(exchange.getRequestURI().getQuery().contains("type=empty"));
            respond(exchange, 200, "{\"code\":0,\"data\":[{\"id\":\"doc-1\"}]}");
            return;
        }
        if ("DELETE".equals(exchange.getRequestMethod())) {
            deleteCalls.incrementAndGet();
            respond(exchange, 200, "{\"code\":0,\"data\":true}");
            return;
        }
        respond(exchange, 405, "{}");
    }

    private void handleChunks(HttpExchange exchange) throws IOException {
        assertEquals("POST", exchange.getRequestMethod());
        chunkCalls.incrementAndGet();
        respond(exchange, 200, "{\"code\":0,\"data\":{\"id\":\"chunk-1\"}}");
    }

    private void handleRetrieval(HttpExchange exchange) throws IOException {
        assertEquals("POST", exchange.getRequestMethod());
        respond(exchange, 200, "{\"code\":0,\"data\":{\"chunks\":[{\"document_id\":\"doc-1\",\"content\":\"销售额定义\",\"similarity\":0.93,\"metadata\":{\"business\":\"sales\"}}]}}");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
