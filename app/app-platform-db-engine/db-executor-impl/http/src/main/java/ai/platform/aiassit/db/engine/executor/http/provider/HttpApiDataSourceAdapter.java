package ai.platform.aiassit.db.engine.executor.http.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DataSourceCapabilities;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.request.DataReadCommand;
import ai.platform.aiassit.db.engine.executor.spi.result.DataReadResult;
import ai.platform.aiassit.db.engine.executor.spi.provider.DataSourceAdapter;
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
 * HTTP API 数据源读取适配器。
 *
 * <p>endpoint 是数据源配置的受控基地址，command.resource 只允许相对路径。响应映射可通过
 * attributes.responseRecordsPath / attributes.responseTotalPath 配置简单点路径。</p>
 */
@Component
public class HttpApiDataSourceAdapter implements DataSourceAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public DbAccessSourceType sourceType() {
        return DbAccessSourceType.HTTP_API;
    }

    @Override
    public DataSourceCapabilities capabilities() {
        return DataSourceCapabilities.readOnly();
    }

    @Override
    public DataReadResult read(DbAccessContext context, DataReadCommand command) throws DbAccessException {
        if (command == null) {
            throw new DbAccessException("HTTP API 读取命令不能为空");
        }
        URI uri = buildUri(context, command);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "application/json");
        applyConfiguredHeaders(request, context.getAttributes());
        int connectTimeoutMs = context.getNetwork() == null || context.getNetwork().getConnectTimeoutMs() == null
                ? 5_000 : context.getNetwork().getConnectTimeoutMs();
        int readTimeoutMs = context.getNetwork() == null || context.getNetwork().getReadTimeoutMs() == null
                ? 15_000 : context.getNetwork().getReadTimeoutMs();
        request.timeout(Duration.ofMillis(readTimeoutMs));
        try {
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                    .build()
                    .send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DbAccessException("HTTP API 调用失败, status=" + response.statusCode());
            }
            return toResult(response, context.getAttributes());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DbAccessException("HTTP API 调用被中断", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new DbAccessException("HTTP API 调用失败", ex);
        }
    }

    private URI buildUri(DbAccessContext context, DataReadCommand command) throws DbAccessException {
        if (!StringUtils.hasText(context.getEndpoint())) {
            throw new DbAccessException("HTTP API 数据源 endpoint 不能为空");
        }
        String endpoint = context.getEndpoint().trim();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            throw new DbAccessException("HTTP API endpoint 必须使用 http 或 https");
        }
        String resource = command == null ? null : command.getResource();
        if (!StringUtils.hasText(resource) || !resource.startsWith("/") || resource.startsWith("//") || resource.contains("://")) {
            throw new DbAccessException("HTTP API resource 必须为以 / 开头的相对路径");
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (command.getParameters() != null) {
            parameters.putAll(command.getParameters());
        }
        if (command.getPage() != null) {
            parameters.putIfAbsent("page", command.getPage());
        }
        if (command.getPageSize() != null) {
            parameters.putIfAbsent("pageSize", command.getPageSize());
        }
        StringBuilder uri = new StringBuilder(endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint)
                .append(resource);
        appendQueryString(uri, parameters);
        return URI.create(uri.toString());
    }

    private void appendQueryString(StringBuilder uri, Map<String, Object> parameters) {
        boolean first = !uri.toString().contains("?");
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            uri.append(first ? '?' : '&');
            first = false;
            uri.append(encode(entry.getKey())).append('=').append(encode(String.valueOf(entry.getValue())));
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void applyConfiguredHeaders(HttpRequest.Builder request, Map<String, Object> attributes) {
        if (attributes == null || !(attributes.get("headers") instanceof Map<?, ?> headers)) {
            return;
        }
        for (Map.Entry<?, ?> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                request.header(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
    }

    private DataReadResult toResult(HttpResponse<String> response, Map<String, Object> attributes) throws DbAccessException {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode recordsNode = resolveNode(root, attribute(attributes, "responseRecordsPath"));
            if (recordsNode == null) {
                recordsNode = defaultRecordsNode(root);
            }
            List<Map<String, Object>> records = toRecords(recordsNode);
            JsonNode totalNode = resolveNode(root, attribute(attributes, "responseTotalPath"));
            Long total = totalNode != null && totalNode.isNumber() ? totalNode.longValue() : null;
            return DataReadResult.builder()
                    .records(records)
                    .total(total)
                    .statusCode(response.statusCode())
                    .metadata(Map.of("protocol", "HTTP_API"))
                    .build();
        } catch (IOException ex) {
            throw new DbAccessException("HTTP API 响应不是有效 JSON", ex);
        }
    }

    private JsonNode defaultRecordsNode(JsonNode root) {
        if (root.isArray()) {
            return root;
        }
        for (String field : List.of("records", "list", "items", "data")) {
            JsonNode candidate = root.get(field);
            if (candidate != null && (candidate.isArray() || candidate.isObject())) {
                return candidate;
            }
        }
        return root;
    }

    private List<Map<String, Object>> toRecords(JsonNode node) {
        List<Map<String, Object>> records = new ArrayList<>();
        if (node == null || node.isNull()) {
            return records;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                records.add(objectMapper.convertValue(item, new TypeReference<LinkedHashMap<String, Object>>() { }));
            }
            return records;
        }
        records.add(objectMapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() { }));
        return records;
    }

    private JsonNode resolveNode(JsonNode root, String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        JsonNode current = root;
        for (String segment : path.replaceFirst("^\\$\\.?", "").split("\\.")) {
            if (!StringUtils.hasText(segment) || current == null) {
                continue;
            }
            current = current.get(segment);
        }
        return current;
    }

    private String attribute(Map<String, Object> attributes, String key) {
        return attributes == null || attributes.get(key) == null ? null : String.valueOf(attributes.get(key));
    }
}
