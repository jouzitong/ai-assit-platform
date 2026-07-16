package ai.platform.aiassit.service.ai.provider.client;

import ai.platform.aiassit.service.ai.api.dto.KbDocument;
import ai.platform.aiassit.service.ai.api.dto.KbSearchItem;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetSaveRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbEmbeddingModelDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbEmbeddingModelListRequest;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.provider.config.RagflowProperties;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderKbSearchRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAGFlow HTTP API 客户端。
 *
 * <p>统一 SPI 的文档写入会根据 providerDocumentId 选择新增或更新：首次同步时创建空
 * Document 和正文 Chunk；再次同步时保留原 Document，并更新其正文 Chunk。</p>
 */
@Slf4j
@Component
public class RagflowKnowledgeBaseClient {

    private static final String PROVIDER_DOCUMENT_ID = "providerDocumentId";
    private static final int CHUNK_PAGE_SIZE = 100;

    private final RagflowProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RagflowKnowledgeBaseClient(RagflowProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs()))
                .build();
    }

    public UpsertResult upsert(String datasetId, List<KbDocument> documents, RequestMeta meta) throws Exception {
        requireDatasetId(datasetId);
        List<String> failedDocumentIds = new ArrayList<>();
        Map<String, String> documentIdMappings = new LinkedHashMap<>();
        int accepted = 0;
        for (KbDocument document : documents) {
            String localDocumentId = document == null ? null : document.getDocumentId();
            try {
                if (document == null || !StringUtils.hasText(localDocumentId) || !StringUtils.hasText(document.getContent())) {
                    throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_CONTENT);
                }
                String providerDocumentId = metadataText(document, PROVIDER_DOCUMENT_ID);
                if (StringUtils.hasText(providerDocumentId)) {
                    providerDocumentId = providerDocumentId.trim();
                    updateDocument(datasetId, providerDocumentId, document, meta);
                } else {
                    providerDocumentId = createEmptyDocument(datasetId, document, meta);
                    try {
                        addChunk(datasetId, providerDocumentId, document, meta);
                    } catch (Exception ex) {
                        deleteDocumentQuietly(datasetId, providerDocumentId, meta);
                        throw ex;
                    }
                }
                documentIdMappings.put(localDocumentId, providerDocumentId);
                accepted++;
            } catch (Exception ex) {
                failedDocumentIds.add(localDocumentId == null ? "" : localDocumentId);
                log.warn("ragflow kb document upsert failed, datasetId={}, documentId={}", datasetId, localDocumentId, ex);
            }
        }
        return new UpsertResult(datasetId, accepted, failedDocumentIds, documentIdMappings);
    }

    public int deleteDocuments(String datasetId, List<String> documentIds, RequestMeta meta) throws Exception {
        requireDatasetId(datasetId);
        if (documentIds == null || documentIds.isEmpty()) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_DOCUMENT_ID);
        }
        request("DELETE", "/api/v1/datasets/" + encodePath(datasetId) + "/documents",
                Map.of("ids", documentIds), meta);
        return documentIds.size();
    }

    private void deleteDocumentQuietly(String datasetId, String documentId, RequestMeta meta) {
        if (!StringUtils.hasText(documentId)) {
            return;
        }
        try {
            deleteDocuments(datasetId, List.of(documentId), meta);
        } catch (Exception cleanupError) {
            log.warn("ragflow cleanup newly created document failed, datasetId={}, documentId={}",
                    datasetId, documentId, cleanupError);
        }
    }

    public List<KbSearchItem> search(String datasetId, String query, Integer pageSize, RequestMeta meta) throws Exception {
        ProviderKbSearchRequest request = new ProviderKbSearchRequest();
        request.setKbId(datasetId);
        request.setQuery(query);
        request.setTopK(pageSize);
        request.setMeta(meta);
        return searchDetailed(request).items();
    }

    /** 按统一检索请求映射 RAGFlow retrieval API 参数。 */
    public List<KbSearchItem> search(ProviderKbSearchRequest request) throws Exception {
        return searchDetailed(request).items();
    }

    /** 执行检索并保留 RAGFlow 返回的总命中数。 */
    public SearchResult searchDetailed(ProviderKbSearchRequest request) throws Exception {
        String datasetId = request == null ? null : request.getKbId();
        String query = request == null ? null : request.getQuery();
        requireDatasetId(datasetId);
        if (!StringUtils.hasText(query)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_MESSAGE);
        }
        RequestMeta meta = request.getMeta();
        int resultSize = positive(request.getPageSize(), positive(request.getTopK(), 5));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", query);
        body.put("dataset_ids", List.of(datasetId));
        body.put("page", positive(request.getPage(), 1));
        body.put("page_size", resultSize);
        body.put("similarity_threshold", ratio(request.getSimilarityThreshold(),
                decimalMeta(meta, "similarityThreshold", 0.2D)));
        body.put("vector_similarity_weight", ratio(request.getVectorSimilarityWeight(),
                decimalMeta(meta, "vectorSimilarityWeight", 0.4D)));
        body.put("top_k", positive(request.getRetrievalTopK(), integerMeta(meta, "retrievalTopK", 1024)));
        body.put("keyword", request.getKeyword() == null
                ? booleanMeta(meta, "keyword", true) : request.getKeyword());
        body.put("highlight", request.getHighlight() == null
                ? booleanMeta(meta, "highlight", true) : request.getHighlight());
        body.put("use_kg", request.getUseKg() == null
                ? booleanMeta(meta, "useKg", false) : request.getUseKg());
        body.put("toc_enhance", request.getTocEnhance() == null
                ? booleanMeta(meta, "tocEnhance", false) : request.getTocEnhance());
        putTextList(body, "document_ids", request.getDocumentIds());
        putTextList(body, "cross_languages", request.getCrossLanguages());
        if (request.getMetadataCondition() != null && !request.getMetadataCondition().isEmpty()) {
            body.put("metadata_condition", request.getMetadataCondition());
        }
        String rerankId = StringUtils.hasText(request.getRerankId())
                ? request.getRerankId().trim() : metaText(meta, "rerankId");
        if (StringUtils.hasText(rerankId)) {
            body.put("rerank_id", rerankId);
        }
        JsonNode data = data(request("POST", "/api/v1/retrieval", body, meta));
        JsonNode chunks = data.path("chunks");
        if (!chunks.isArray() && data.isArray()) {
            chunks = data;
        }
        List<KbSearchItem> items = new ArrayList<>();
        if (!chunks.isArray()) {
            return new SearchResult(items, 0);
        }
        for (JsonNode chunk : chunks) {
            items.add(toSearchItem(chunk));
        }
        int total = data.path("total").isNumber() ? data.path("total").asInt() : items.size();
        return new SearchResult(items, total);
    }

    /** 查询 RAGFlow Dataset 列表，Dataset ID 即业务侧使用的 kbId。 */
    public List<AiKbDatasetDTO> listDatasets(AiKbDatasetListRequest request) throws Exception {
        AiKbDatasetListRequest normalized = request == null ? new AiKbDatasetListRequest() : request;
        int page = normalized.getPage() == null || normalized.getPage() <= 0 ? 1 : normalized.getPage();
        int pageSize = normalized.getPageSize() == null || normalized.getPageSize() <= 0
                ? 30 : normalized.getPageSize();
        StringBuilder path = new StringBuilder("/api/v1/datasets?page=")
                .append(page)
                .append("&page_size=")
                .append(pageSize)
                .append("&include_parsing_status=")
                .append(Boolean.TRUE.equals(normalized.getIncludeParsingStatus()));
        if (StringUtils.hasText(normalized.getName())) {
            path.append("&name=").append(URLEncoder.encode(normalized.getName().trim(), StandardCharsets.UTF_8));
        }
        JsonNode data = data(request("GET", path.toString(), null, normalized.getMeta()));
        JsonNode datasets = datasetItems(data);
        List<AiKbDatasetDTO> result = new ArrayList<>();
        for (JsonNode dataset : datasets) {
            if (dataset.isObject()) {
                result.add(toDataset(dataset));
            }
        }
        return result;
    }

    /** 查询 RAGFlow 已添加的 Embedding 模型。 */
    public List<AiKbEmbeddingModelDTO> listEmbeddingModels(AiKbEmbeddingModelListRequest request) throws Exception {
        AiKbEmbeddingModelListRequest normalized = request == null ? new AiKbEmbeddingModelListRequest() : request;
        JsonNode data = data(request("GET", "/api/v1/models?type=embedding", null, normalized.getMeta()));
        JsonNode models = modelItems(data);
        List<AiKbEmbeddingModelDTO> result = new ArrayList<>();
        for (JsonNode model : models) {
            if (!model.isObject()) {
                continue;
            }
            AiKbEmbeddingModelDTO item = toEmbeddingModel(model);
            if (StringUtils.hasText(item.getValue())) {
                result.add(item);
            }
        }
        return result;
    }

    /** 创建 RAGFlow Dataset。 */
    public AiKbDatasetDTO createDataset(AiKbDatasetSaveRequest request) throws Exception {
        AiKbDatasetSaveRequest normalized = request == null ? new AiKbDatasetSaveRequest() : request;
        if (!StringUtils.hasText(normalized.getName())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KB_ID);
        }
        JsonNode data = data(request("POST", "/api/v1/datasets", datasetPayload(normalized, true), normalized.getMeta()));
        return toDataset(data, null);
    }

    /** 更新 RAGFlow Dataset 配置。 */
    public AiKbDatasetDTO updateDataset(String datasetId, AiKbDatasetSaveRequest request) throws Exception {
        requireDatasetId(datasetId);
        AiKbDatasetSaveRequest normalized = request == null ? new AiKbDatasetSaveRequest() : request;
        JsonNode data = data(request("PUT", "/api/v1/datasets/" + encodePath(datasetId),
                datasetPayload(normalized, false), normalized.getMeta()));
        return toDataset(data, datasetId);
    }

    /** 删除一个或多个 RAGFlow Dataset。 */
    public int deleteDatasets(AiKbDatasetDeleteRequest request) throws Exception {
        AiKbDatasetDeleteRequest normalized = request == null ? new AiKbDatasetDeleteRequest() : request;
        boolean deleteAll = Boolean.TRUE.equals(normalized.getDeleteAll());
        List<String> datasetIds = normalized.getKbIds() == null ? List.of() : normalized.getKbIds().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (!deleteAll && datasetIds.isEmpty()) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KB_ID);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (deleteAll) {
            body.put("delete_all", true);
        } else {
            body.put("ids", datasetIds);
        }
        request("DELETE", "/api/v1/datasets", body, normalized.getMeta());
        return deleteAll ? 0 : datasetIds.size();
    }

    private String createEmptyDocument(String datasetId, KbDocument document, RequestMeta meta) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", documentName(document));
        JsonNode data = data(request("POST", "/api/v1/datasets/" + encodePath(datasetId)
                + "/documents?type=empty", body, meta));
        String documentId = firstText(data, "id", "document_id", "documentId");
        if (!StringUtils.hasText(documentId) && data.isArray() && !data.isEmpty()) {
            documentId = firstText(data.get(0), "id", "document_id", "documentId");
        }
        if (!StringUtils.hasText(documentId)) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "RAGFlow document id is empty");
        }
        return documentId;
    }

    private void updateDocument(String datasetId, String documentId, KbDocument document, RequestMeta meta) throws Exception {
        String documentPath = "/api/v1/datasets/" + encodePath(datasetId) + "/documents/" + encodePath(documentId);
        request("PUT", documentPath, Map.of("name", documentName(document)), meta);

        List<String> chunkIds = listChunkIds(documentPath, meta);
        if (chunkIds.isEmpty()) {
            addChunk(datasetId, documentId, document, meta);
            return;
        }
        if (chunkIds.size() > 1) {
            request("DELETE", documentPath + "/chunks",
                    Map.of("chunk_ids", chunkIds.subList(1, chunkIds.size())), meta);
        }
        request("PATCH", documentPath + "/chunks/" + encodePath(chunkIds.get(0)), chunkPayload(document), meta);
    }

    private List<String> listChunkIds(String documentPath, RequestMeta meta) throws Exception {
        List<String> result = new ArrayList<>();
        int page = 1;
        while (true) {
            JsonNode data = data(request("GET", documentPath + "/chunks?page=" + page
                    + "&page_size=" + CHUNK_PAGE_SIZE, null, meta));
            JsonNode chunks = data.isArray() ? data : data.path("chunks");
            if (!chunks.isArray() || chunks.isEmpty()) {
                break;
            }
            int previousSize = result.size();
            for (JsonNode chunk : chunks) {
                String chunkId = firstText(chunk, "id", "chunk_id", "chunkId");
                if (StringUtils.hasText(chunkId) && !result.contains(chunkId.trim())) {
                    result.add(chunkId.trim());
                }
            }
            int total = data.path("total").asInt(-1);
            if (chunks.size() < CHUNK_PAGE_SIZE
                    || (total >= 0 && result.size() >= total)
                    || result.size() == previousSize) {
                break;
            }
            page++;
        }
        return result;
    }

    private void addChunk(String datasetId, String documentId, KbDocument document, RequestMeta meta) throws Exception {
        request("POST", "/api/v1/datasets/" + encodePath(datasetId) + "/documents/"
                + encodePath(documentId) + "/chunks", chunkPayload(document), meta);
    }

    private Map<String, Object> chunkPayload(KbDocument document) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", document.getContent());
        copyListMetadata(document, "importantKeywords", "important_keywords", body);
        copyListMetadata(document, "tagKeywords", "tag_kwd", body);
        copyListMetadata(document, "questions", "questions", body);
        return body;
    }

    private JsonNode request(String method, String path, Object body, RequestMeta meta) throws Exception {
        String baseUrl = resolveBaseUrl(meta);
        String payload = body == null ? null : objectMapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(timeoutMs()))
                .header("Accept", "application/json");
        applyAuthHeader(builder, meta);
        if (payload != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, payload == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(payload));
        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } catch (IOException ex) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, ex.getMessage());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED,
                    "RAGFlow HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode code = root.get("code");
        if (!isSuccessfulResponseCode(code)) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED,
                    "RAGFlow error: " + firstText(root, "message", "msg"));
        }
        return root;
    }

    private boolean isSuccessfulResponseCode(JsonNode code) {
        if (code == null || code.isNull()) {
            return true;
        }
        if (code.isNumber()) {
            return code.decimalValue().compareTo(java.math.BigDecimal.ZERO) == 0;
        }
        return code.isTextual() && "0".equals(code.textValue().trim());
    }

    private JsonNode data(JsonNode root) {
        return root == null ? objectMapper.nullNode() : root.path("data");
    }

    private KbSearchItem toSearchItem(JsonNode chunk) {
        KbSearchItem item = new KbSearchItem();
        item.setDocumentId(firstText(chunk, "document_id", "doc_id", "documentId", "id"));
        JsonNode score = chunk.has("similarity") ? chunk.get("similarity") : chunk.get("score");
        if (score != null && score.isNumber()) {
            item.setScore(score.asDouble());
        }
        item.setContent(firstText(chunk, "content", "content_with_weight"));
        Map<String, Object> metadata = objectMapper.convertValue(chunk, new TypeReference<LinkedHashMap<String, Object>>() { });
        JsonNode documentMetadata = chunk.get("metadata");
        if (documentMetadata != null && documentMetadata.isObject()) {
            metadata.putAll(objectMapper.convertValue(documentMetadata,
                    new TypeReference<LinkedHashMap<String, Object>>() { }));
        }
        item.setMetadata(metadata);
        return item;
    }

    private JsonNode datasetItems(JsonNode data) {
        if (data != null && data.isArray()) {
            return data;
        }
        if (data != null && data.isObject()) {
            for (String field : List.of("datasets", "list", "items")) {
                JsonNode items = data.get(field);
                if (items != null && items.isArray()) {
                    return items;
                }
            }
        }
        return objectMapper.createArrayNode();
    }

    private JsonNode modelItems(JsonNode data) {
        if (data != null && data.isArray()) {
            return data;
        }
        if (data != null && data.isObject()) {
            for (String field : List.of("models", "list", "items")) {
                JsonNode items = data.get(field);
                if (items != null && items.isArray()) {
                    return items;
                }
            }
        }
        return objectMapper.createArrayNode();
    }

    private Map<String, Object> datasetPayload(AiKbDatasetSaveRequest request, boolean creating) {
        Map<String, Object> body = request.getExt() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.getExt());
        putText(body, "name", request.getName(), creating);
        putText(body, "description", request.getDescription(), false);
        putText(body, "embedding_model", request.getEmbeddingModel(), false);
        putText(body, "permission", request.getPermission(), false);
        putText(body, "chunk_method", request.getChunkMethod(), false);
        putText(body, "parse_type", request.getParseType(), false);
        putText(body, "pipeline_id", request.getPipelineId(), false);
        if (request.getParserConfig() != null && !request.getParserConfig().isEmpty()) {
            body.put("parser_config", request.getParserConfig());
        }
        return body;
    }

    private void putText(Map<String, Object> target, String field, String value, boolean required) {
        if (StringUtils.hasText(value)) {
            target.put(field, value.trim());
        } else if (required) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KB_ID);
        }
    }

    private AiKbDatasetDTO toDataset(JsonNode dataset) {
        return toDataset(dataset, null);
    }

    private AiKbDatasetDTO toDataset(JsonNode dataset, String fallbackKbId) {
        AiKbDatasetDTO result = new AiKbDatasetDTO();
        result.setKbId(firstText(dataset, "id", "dataset_id", "datasetId"));
        if (!StringUtils.hasText(result.getKbId())) {
            result.setKbId(fallbackKbId);
        }
        result.setKbName(firstText(dataset, "name", "dataset_name", "datasetName"));
        result.setClientType(AiKnowledgeClientType.RAGFLOW);
        result.setDescription(firstText(dataset, "description"));
        result.setEmbeddingModel(firstText(dataset, "embedding_model", "embeddingModel"));
        result.setChunkMethod(firstText(dataset, "chunk_method", "chunkMethod"));
        result.setPermission(firstText(dataset, "permission"));
        result.setDocumentCount(integerField(dataset, "document_count", "documentCount"));
        result.setChunkCount(integerField(dataset, "chunk_count", "chunkCount"));
        result.setExt(dataset != null && dataset.isObject()
                ? objectMapper.convertValue(dataset, new TypeReference<LinkedHashMap<String, Object>>() { })
                : new LinkedHashMap<>());
        return result;
    }

    private AiKbEmbeddingModelDTO toEmbeddingModel(JsonNode model) {
        AiKbEmbeddingModelDTO result = new AiKbEmbeddingModelDTO();
        result.setModelId(firstText(model, "model_id", "modelId", "id"));
        result.setName(firstText(model, "name", "model_name", "modelName"));
        result.setProviderName(firstText(model, "provider_name", "providerName", "model_provider", "modelProvider"));
        result.setInstanceName(firstText(model, "instance_name", "instanceName", "model_instance", "modelInstance"));
        result.setModelTypes(textList(model, "model_type", "modelType", "model_types", "modelTypes"));
        result.setEnabled(booleanField(model, "enable", "enabled"));
        result.setValue(firstText(result.getModelId(), compositeModelValue(result)));
        result.setExt(model != null && model.isObject()
                ? objectMapper.convertValue(model, new TypeReference<LinkedHashMap<String, Object>>() { })
                : new LinkedHashMap<>());
        return result;
    }

    private String compositeModelValue(AiKbEmbeddingModelDTO model) {
        if (model == null || !StringUtils.hasText(model.getName()) || !StringUtils.hasText(model.getProviderName())) {
            return null;
        }
        if (StringUtils.hasText(model.getInstanceName())) {
            return model.getName().trim() + "@" + model.getInstanceName().trim() + "@" + model.getProviderName().trim();
        }
        return model.getName().trim() + "@" + model.getProviderName().trim();
    }

    private List<String> textList(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isArray()) {
                List<String> result = new ArrayList<>();
                value.forEach(item -> {
                    if (item.isValueNode() && StringUtils.hasText(item.asText())) {
                        result.add(item.asText());
                    }
                });
                return result;
            }
            if (value.isValueNode() && StringUtils.hasText(value.asText())) {
                return List.of(value.asText());
            }
        }
        return List.of();
    }

    private Boolean booleanField(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isValueNode()) {
                return value.asBoolean();
            }
        }
        return null;
    }

    private Integer integerField(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.canConvertToInt()) {
                return value.intValue();
            }
        }
        return null;
    }

    private String resolveBaseUrl(RequestMeta meta) {
        String baseUrl = firstText(metaText(meta, "ragflowBaseUrl"), metaText(meta, "kbEndpoint"), properties.getBaseUrl());
        if (!StringUtils.hasText(baseUrl)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_BASE_URL);
        }
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_BASE_URL);
        }
        return normalized;
    }

    private void applyAuthHeader(HttpRequest.Builder builder, RequestMeta meta) {
        Object configuredAuth = meta == null || meta.getExt() == null ? null : meta.getExt().get("knowledgeClientAuth");
        if (configuredAuth instanceof Map<?, ?> auth) {
            String type = firstText(text(auth.get("type")), "header").toLowerCase();
            String value = text(auth.get("value"));
            if ("none".equals(type)) {
                return;
            }
            if (!StringUtils.hasText(value)) {
                throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_API_KEY);
            }
            String headerName = firstText(text(auth.get("headerName")), "Authorization");
            String prefix = text(auth.get("prefix"));
            if (!StringUtils.hasText(prefix) && "bearer".equals(type)) {
                prefix = "Bearer ";
            }
            builder.header(headerName, (prefix == null ? "" : prefix) + value);
            return;
        }

        String apiKey = firstText(metaText(meta, "ragflowApiKey"), properties.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_API_KEY);
        }
        builder.header("Authorization", "Bearer " + apiKey.trim());
    }

    private int timeoutMs() {
        return properties.getTimeoutMs() == null || properties.getTimeoutMs() <= 0 ? 30000 : properties.getTimeoutMs();
    }

    private void requireDatasetId(String datasetId) {
        if (!StringUtils.hasText(datasetId)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KB_ID);
        }
    }

    private String documentName(KbDocument document) {
        String name = metadataText(document, "documentName");
        return StringUtils.hasText(name) ? name : document.getDocumentId() + ".md";
    }

    private void copyListMetadata(KbDocument document, String sourceKey, String targetKey, Map<String, Object> body) {
        Object value = document.getMetadata() == null ? null : document.getMetadata().get(sourceKey);
        if (value instanceof List<?> values && !values.isEmpty()) {
            body.put(targetKey, values);
        }
    }

    private String metadataText(KbDocument document, String key) {
        return document == null || document.getMetadata() == null ? null : text(document.getMetadata().get(key));
    }

    private String metaText(RequestMeta meta, String key) {
        return meta == null || meta.getExt() == null ? null : text(meta.getExt().get(key));
    }

    private double decimalMeta(RequestMeta meta, String key, double defaultValue) {
        Object value = meta == null || meta.getExt() == null ? null : meta.getExt().get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? defaultValue : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private int integerMeta(RequestMeta meta, String key, int defaultValue) {
        Object value = meta == null || meta.getExt() == null ? null : meta.getExt().get(key);
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        try {
            int parsed = value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private boolean booleanMeta(RequestMeta meta, String key, boolean defaultValue) {
        Object value = meta == null || meta.getExt() == null ? null : meta.getExt().get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private int positive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private double ratio(Double value, double defaultValue) {
        return value == null || !Double.isFinite(value) || value < 0D || value > 1D ? defaultValue : value;
    }

    private void putTextList(Map<String, Object> body, String field, List<String> values) {
        if (values == null) {
            return;
        }
        List<String> normalized = values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (!normalized.isEmpty()) {
            body.put(field, normalized);
        }
    }

    private String encodePath(String value) {
        return value.replace("%", "%25").replace("/", "%2F");
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isValueNode() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        String text = value == null ? null : String.valueOf(value);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    public record UpsertResult(String kbId, int accepted, List<String> failedDocumentIds,
                               Map<String, String> documentIdMappings) {
    }

    public record SearchResult(List<KbSearchItem> items, int total) {
    }
}
