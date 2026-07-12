package ai.platform.aiassit.service.ai.provider.client;

import ai.platform.aiassit.service.ai.api.dto.KbDocument;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
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
import java.util.Map;
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
        server.createContext("/api/v1/datasets", this::handleDatasets);
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

    @Test
    void shouldListDatasetsAndExposeDatasetIdAsKbId() throws Exception {
        AiKbDatasetListRequest request = new AiKbDatasetListRequest();
        request.setPage(2);
        request.setPageSize(10);
        request.setName("销售 知识库");

        List<AiKbDatasetDTO> datasets = client.listDatasets(request);

        assertEquals(1, datasets.size());
        AiKbDatasetDTO dataset = datasets.get(0);
        assertEquals("dataset-1", dataset.getKbId());
        assertEquals("销售知识库", dataset.getKbName());
        assertEquals(2, dataset.getDocumentCount());
        assertEquals(8, dataset.getChunkCount());
        assertEquals("RAGFlow 对外知识库", dataset.getDescription());
    }

    @Test
    void shouldUseConfiguredCustomAuthHeaderWhenListingDatasets() throws Exception {
        AiKbDatasetListRequest request = new AiKbDatasetListRequest();
        request.setPage(2);
        request.setPageSize(10);
        request.setName("销售 知识库");
        request.getMeta().getExt().put("knowledgeClientAuth", Map.of(
                "type", "header",
                "headerName", "X-API-Key",
                "value", "custom-key"
        ));

        List<AiKbDatasetDTO> datasets = client.listDatasets(request);

        assertEquals(1, datasets.size());
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
        respond(exchange, 200, "{\"code\":\"0\",\"data\":{\"id\":\"chunk-1\"}}");
    }

    private void handleRetrieval(HttpExchange exchange) throws IOException {
        assertEquals("POST", exchange.getRequestMethod());
        respond(exchange, 200, "{\"code\":0,\"data\":{\"chunks\":[{\"document_id\":\"doc-1\",\"content\":\"销售额定义\",\"similarity\":0.93,\"metadata\":{\"business\":\"sales\"}}]}}");
    }

    private void handleDatasets(HttpExchange exchange) throws IOException {
        assertEquals("GET", exchange.getRequestMethod());
        if (exchange.getRequestHeaders().getFirst("X-API-Key") != null) {
            assertEquals("custom-key", exchange.getRequestHeaders().getFirst("X-API-Key"));
        } else {
            assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
        }
        String query = exchange.getRequestURI().getRawQuery();
        assertTrue(query.contains("page=2"));
        assertTrue(query.contains("page_size=10"));
        assertTrue(query.contains("include_parsing_status=true"));
        assertTrue(query.contains("name=%E9%94%80%E5%94%AE+%E7%9F%A5%E8%AF%86%E5%BA%93"));
        respond(exchange, 200, "{\"code\":0,\"data\":[{\"id\":\"dataset-1\",\"name\":\"销售知识库\",\"description\":\"RAGFlow 对外知识库\",\"embedding_model\":\"BAAI/bge-m3\",\"chunk_method\":\"naive\",\"permission\":\"team\",\"document_count\":2,\"chunk_count\":8}]}");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
