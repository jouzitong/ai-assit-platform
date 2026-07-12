package ai.platform.aiassit.service.ai.provider.client;

import ai.platform.aiassit.service.ai.api.dto.KbDocument;
import ai.platform.aiassit.service.ai.api.dto.KbSearchItem;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetSaveRequest;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.provider.config.RagflowProperties;
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
 * <p>统一 SPI 的文档写入会映射为：创建空 Document，再创建一个承载正文的 Chunk。
 * 文档内容发生变更时，调用方传入原 providerDocumentId 后会先删除旧 Document，符合
 * RAGFlow 推荐的“删除后重建”更新方式。</p>
 */
@Slf4j
@Component
public class RagflowKnowledgeBaseClient {

    private static final String PROVIDER_DOCUMENT_ID = "providerDocumentId";

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
                String previousDocumentId = metadataText(document, PROVIDER_DOCUMENT_ID);
                if (StringUtils.hasText(previousDocumentId)) {
                    deleteDocuments(datasetId, List.of(previousDocumentId), meta);
                }
                String providerDocumentId = createEmptyDocument(datasetId, document, meta);
                addChunk(datasetId, providerDocumentId, document, meta);
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

    public List<KbSearchItem> search(String datasetId, String query, Integer pageSize, RequestMeta meta) throws Exception {
        requireDatasetId(datasetId);
        if (!StringUtils.hasText(query)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_MESSAGE);
        }
        int resultSize = pageSize == null || pageSize <= 0 ? 5 : pageSize;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", query);
        body.put("dataset_ids", List.of(datasetId));
        body.put("page", 1);
        body.put("page_size", resultSize);
        body.put("similarity_threshold", decimalMeta(meta, "similarityThreshold", 0.2D));
        body.put("vector_similarity_weight", decimalMeta(meta, "vectorSimilarityWeight", 0.4D));
        body.put("top_k", integerMeta(meta, "retrievalTopK", 1024));
        body.put("keyword", booleanMeta(meta, "keyword", true));
        body.put("highlight", booleanMeta(meta, "highlight", true));
        String rerankId = metaText(meta, "rerankId");
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
            return items;
        }
        for (JsonNode chunk : chunks) {
            items.add(toSearchItem(chunk));
        }
        return items;
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

    private void addChunk(String datasetId, String documentId, KbDocument document, RequestMeta meta) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", document.getContent());
        copyListMetadata(document, "importantKeywords", "important_keywords", body);
        copyListMetadata(document, "tagKeywords", "tag_kwd", body);
        copyListMetadata(document, "questions", "questions", body);
        request("POST", "/api/v1/datasets/" + encodePath(datasetId) + "/documents/"
                + encodePath(documentId) + "/chunks", body, meta);
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
}
