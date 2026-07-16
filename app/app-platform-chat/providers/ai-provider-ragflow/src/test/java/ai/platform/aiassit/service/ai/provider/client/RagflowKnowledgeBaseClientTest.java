package ai.platform.aiassit.service.ai.provider.client;

import ai.platform.aiassit.service.ai.api.dto.KbDocument;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetSaveRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbEmbeddingModelDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbEmbeddingModelListRequest;
import ai.platform.aiassit.service.ai.api.dto.KbSearchItem;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.provider.config.RagflowProperties;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderKbSearchRequest;
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
    private final AtomicInteger updateDocumentCalls = new AtomicInteger();
    private final AtomicInteger updateChunkCalls = new AtomicInteger();
    private final AtomicInteger listChunkCalls = new AtomicInteger();
    private final AtomicInteger deleteChunkCalls = new AtomicInteger();
    private volatile boolean chunkShouldFail;
    private volatile boolean multipleChunkPages;
    private volatile JsonNode retrievalRequestBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/datasets/dataset-1/documents", this::handleDocuments);
        server.createContext("/api/v1/datasets/dataset-1/documents/doc-1/chunks", this::handleChunks);
        server.createContext("/api/v1/datasets/dataset-1/documents/doc-old", this::handleExistingDocument);
        server.createContext("/api/v1/datasets", this::handleDatasets);
        server.createContext("/api/v1/models", this::handleModels);
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
    void shouldUpdateExistingDocumentWhenProviderDocumentIdExists() throws Exception {
        multipleChunkPages = true;
        KbDocument document = new KbDocument();
        document.setDocumentId("local-doc-1");
        document.setContent("新的销售额定义");
        document.getMetadata().put("documentName", "updated-metric.md");
        document.getMetadata().put("providerDocumentId", "doc-old");

        RagflowKnowledgeBaseClient.UpsertResult result = client.upsert("dataset-1", List.of(document), new RequestMeta());

        assertEquals(1, result.accepted());
        assertEquals("doc-old", result.documentIdMappings().get("local-doc-1"));
        assertEquals(0, createCalls.get());
        assertEquals(0, chunkCalls.get());
        assertEquals(0, deleteCalls.get());
        assertEquals(1, updateDocumentCalls.get());
        assertEquals(1, updateChunkCalls.get());
        assertEquals(2, listChunkCalls.get());
        assertEquals(1, deleteChunkCalls.get());
    }

    @Test
    void shouldCleanupNewDocumentWhenChunkCreationFails() throws Exception {
        chunkShouldFail = true;
        KbDocument document = new KbDocument();
        document.setDocumentId("local-doc-1");
        document.setContent("无法切片的内容");

        RagflowKnowledgeBaseClient.UpsertResult result = client.upsert("dataset-1", List.of(document), new RequestMeta());

        assertEquals(0, result.accepted());
        assertTrue(result.failedDocumentIds().contains("local-doc-1"));
        assertTrue(result.documentIdMappings().isEmpty());
        assertEquals(1, createCalls.get());
        assertEquals(1, chunkCalls.get());
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

    @Test
    void shouldForwardRagflowRetrievalTestOptions() throws Exception {
        ProviderKbSearchRequest request = new ProviderKbSearchRequest();
        request.setKbId("dataset-1");
        request.setQuery("销售额怎么算？");
        request.setTopK(5);
        request.setPage(2);
        request.setPageSize(12);
        request.setRetrievalTopK(256);
        request.setSimilarityThreshold(0.35D);
        request.setVectorSimilarityWeight(0.7D);
        request.setRerankId("rerank-model");
        request.setKeyword(false);
        request.setHighlight(true);
        request.setUseKg(true);
        request.setTocEnhance(true);
        request.setDocumentIds(List.of("doc-1"));
        request.setCrossLanguages(List.of("English"));
        request.setMetadataCondition(Map.of(
                "logic", "and",
                "conditions", List.of(Map.of("name", "business", "comparison_operator", "=", "value", "sales"))));
        request.setMeta(new RequestMeta());

        RagflowKnowledgeBaseClient.SearchResult result = client.searchDetailed(request);

        assertEquals(1, result.items().size());
        assertEquals(7, result.total());
        assertEquals(2, retrievalRequestBody.path("page").asInt());
        assertEquals(12, retrievalRequestBody.path("page_size").asInt());
        assertEquals(256, retrievalRequestBody.path("top_k").asInt());
        assertEquals(0.35D, retrievalRequestBody.path("similarity_threshold").asDouble());
        assertEquals(0.7D, retrievalRequestBody.path("vector_similarity_weight").asDouble());
        assertEquals("rerank-model", retrievalRequestBody.path("rerank_id").asText());
        assertTrue(retrievalRequestBody.path("highlight").asBoolean());
        assertTrue(retrievalRequestBody.path("use_kg").asBoolean());
        assertTrue(retrievalRequestBody.path("toc_enhance").asBoolean());
        assertEquals("doc-1", retrievalRequestBody.path("document_ids").path(0).asText());
        assertEquals("English", retrievalRequestBody.path("cross_languages").path(0).asText());
        assertEquals("sales", retrievalRequestBody.path("metadata_condition").path("conditions").path(0).path("value").asText());
    }

    @Test
    void shouldListEmbeddingModelsAndExposeValidDatasetValue() throws Exception {
        List<AiKbEmbeddingModelDTO> models = client.listEmbeddingModels(new AiKbEmbeddingModelListRequest());

        assertEquals(2, models.size());
        assertEquals("0123456789abcdef0123456789abcdef", models.get(0).getValue());
        assertEquals("text-embedding-3-large", models.get(0).getName());
        assertEquals("OpenAI", models.get(0).getProviderName());
        assertEquals("tei-bge-m3@default@Builtin", models.get(1).getValue());
    }

    @Test
    void shouldCreateUpdateAndDeleteDatasets() throws Exception {
        AiKbDatasetSaveRequest createRequest = new AiKbDatasetSaveRequest();
        createRequest.setName("销售知识库");
        createRequest.setDescription("销售指标说明");
        createRequest.setEmbeddingModel("BAAI/bge-m3@BAAI");
        createRequest.setChunkMethod("naive");
        createRequest.setParserConfig(Map.of("chunk_token_num", 512));
        createRequest.setExt(Map.of("pagerank", 10));

        AiKbDatasetDTO created = client.createDataset(createRequest);
        assertEquals("dataset-2", created.getKbId());
        assertEquals("销售知识库", created.getKbName());

        AiKbDatasetSaveRequest updateRequest = new AiKbDatasetSaveRequest();
        updateRequest.setDescription("更新后的销售指标说明");
        AiKbDatasetDTO updated = client.updateDataset("dataset-2", updateRequest);
        assertEquals("dataset-2", updated.getKbId());
        assertEquals("更新后的销售知识库", updated.getKbName());

        AiKbDatasetDeleteRequest deleteRequest = new AiKbDatasetDeleteRequest();
        deleteRequest.setKbIds(List.of("dataset-2"));
        assertEquals(1, client.deleteDatasets(deleteRequest));
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
        if (chunkShouldFail) {
            respond(exchange, 500, "{\"code\":500,\"message\":\"chunk failed\"}");
            return;
        }
        respond(exchange, 200, "{\"code\":\"0\",\"data\":{\"id\":\"chunk-1\"}}");
    }

    private void handleExistingDocument(HttpExchange exchange) throws IOException {
        assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
        String path = exchange.getRequestURI().getPath();
        if ("/api/v1/datasets/dataset-1/documents/doc-old".equals(path)
                && "PUT".equals(exchange.getRequestMethod())) {
            updateDocumentCalls.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"name\":\"updated-metric.md\""));
            respond(exchange, 200, "{\"code\":0,\"data\":true}");
            return;
        }
        if (path.endsWith("/chunks") && "GET".equals(exchange.getRequestMethod())) {
            listChunkCalls.incrementAndGet();
            String query = exchange.getRequestURI().getRawQuery();
            if (multipleChunkPages) {
                if ("page=1&page_size=100".equals(query)) {
                    respond(exchange, 200, chunkListResponse(0, 100, 101));
                    return;
                }
                assertEquals("page=2&page_size=100", query);
                respond(exchange, 200, chunkListResponse(100, 1, 101));
                return;
            }
            assertEquals("page=1&page_size=100", query);
            respond(exchange, 200, chunkListResponse(0, 1, 1));
            return;
        }
        if (path.endsWith("/chunks") && "DELETE".equals(exchange.getRequestMethod())) {
            deleteChunkCalls.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"chunk-100\""));
            respond(exchange, 200, "{\"code\":0,\"data\":true}");
            return;
        }
        if (path.endsWith("/chunks/chunk-old") && "PATCH".equals(exchange.getRequestMethod())) {
            updateChunkCalls.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"content\":\"新的销售额定义\""));
            respond(exchange, 200, "{\"code\":0}");
            return;
        }
        respond(exchange, 405, "{}");
    }

    private String chunkListResponse(int start, int count, int total) {
        StringBuilder chunks = new StringBuilder();
        for (int index = start; index < start + count; index++) {
            if (!chunks.isEmpty()) {
                chunks.append(',');
            }
            String chunkId = index == 0 ? "chunk-old" : "chunk-" + index;
            chunks.append("{\"id\":\"").append(chunkId).append("\"}");
        }
        return "{\"code\":0,\"data\":{\"chunks\":[" + chunks + "],\"total\":" + total + "}}";
    }

    private void handleRetrieval(HttpExchange exchange) throws IOException {
        assertEquals("POST", exchange.getRequestMethod());
        retrievalRequestBody = new ObjectMapper().readTree(exchange.getRequestBody());
        respond(exchange, 200, "{\"code\":0,\"data\":{\"total\":7,\"chunks\":[{\"document_id\":\"doc-1\",\"content\":\"销售额定义\",\"similarity\":0.93,\"metadata\":{\"business\":\"sales\"}}]}}");
    }

    private void handleDatasets(HttpExchange exchange) throws IOException {
        if (exchange.getRequestHeaders().getFirst("X-API-Key") != null) {
            assertEquals("custom-key", exchange.getRequestHeaders().getFirst("X-API-Key"));
        } else {
            assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
        }
        if ("POST".equals(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"name\":\"销售知识库\""));
            assertTrue(body.contains("\"embedding_model\":\"BAAI/bge-m3@BAAI\""));
            assertTrue(body.contains("\"parser_config\":{\"chunk_token_num\":512}"));
            assertTrue(body.contains("\"pagerank\":10"));
            respond(exchange, 200, "{\"code\":0,\"data\":{\"id\":\"dataset-2\",\"name\":\"销售知识库\"}}");
            return;
        }
        if ("PUT".equals(exchange.getRequestMethod())) {
            assertEquals("/api/v1/datasets/dataset-2", exchange.getRequestURI().getPath());
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"description\":\"更新后的销售指标说明\""));
            respond(exchange, 200, "{\"code\":0,\"data\":{\"id\":\"dataset-2\",\"name\":\"更新后的销售知识库\"}}");
            return;
        }
        if ("DELETE".equals(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"ids\":[\"dataset-2\"]"));
            respond(exchange, 200, "{\"code\":0,\"data\":true}");
            return;
        }
        assertEquals("GET", exchange.getRequestMethod());
        String query = exchange.getRequestURI().getRawQuery();
        assertTrue(query.contains("page=2"));
        assertTrue(query.contains("page_size=10"));
        assertTrue(query.contains("include_parsing_status=true"));
        assertTrue(query.contains("name=%E9%94%80%E5%94%AE+%E7%9F%A5%E8%AF%86%E5%BA%93"));
        respond(exchange, 200, "{\"code\":0,\"data\":[{\"id\":\"dataset-1\",\"name\":\"销售知识库\",\"description\":\"RAGFlow 对外知识库\",\"embedding_model\":\"BAAI/bge-m3\",\"chunk_method\":\"naive\",\"permission\":\"team\",\"document_count\":2,\"chunk_count\":8}]}");
    }

    private void handleModels(HttpExchange exchange) throws IOException {
        assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
        assertEquals("GET", exchange.getRequestMethod());
        assertEquals("type=embedding", exchange.getRequestURI().getRawQuery());
        respond(exchange, 200, "{\"code\":0,\"data\":{\"models\":["
                + "{\"model_id\":\"0123456789abcdef0123456789abcdef\",\"name\":\"text-embedding-3-large\","
                + "\"provider_name\":\"OpenAI\",\"instance_name\":\"default\",\"model_type\":[\"embedding\"],\"enable\":true},"
                + "{\"model_id\":\"\",\"name\":\"tei-bge-m3\",\"provider_name\":\"Builtin\","
                + "\"instance_name\":\"default\",\"model_type\":[\"embedding\"],\"enable\":true}"
                + "]}}");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
