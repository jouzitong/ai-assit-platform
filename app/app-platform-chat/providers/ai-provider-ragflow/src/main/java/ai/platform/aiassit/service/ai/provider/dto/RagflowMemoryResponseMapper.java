package ai.platform.aiassit.service.ai.provider.dto;

import ai.platform.aiassit.service.ai.api.memory.enums.MemoryProviderType;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryDescriptor;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryMessage;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Maps version-tolerant RAGFlow JSON into the provider-neutral Memory contract. */
@Component
public class RagflowMemoryResponseMapper {

    public MemoryDescriptor descriptor(JsonNode data, String fallbackMemoryId) {
        MemoryDescriptor result = new MemoryDescriptor();
        result.setProviderType(MemoryProviderType.RAGFLOW);
        result.setMemoryId(firstText(data, "id", "memory_id", "memoryId"));
        if (!StringUtils.hasText(result.getMemoryId())) {
            result.setMemoryId(fallbackMemoryId);
        }
        result.setName(firstText(data, "name"));
        result.setMemoryTypes(memoryTypes(data == null ? null : data.get("memory_type")));
        result.setEmbeddingModel(firstText(data, "embd_id", "embedding_model", "embeddingModel"));
        result.setExtractionModel(firstText(data, "llm_id", "extraction_model", "extractionModel"));
        result.setPermission(firstText(data, "permission", "permissions"));
        result.setMemorySize(integerField(data, "memory_size", "memorySize"));
        result.setForgettingPolicy(firstText(data, "forgetting_policy", "forgettingPolicy"));
        return result;
    }

    public List<MemoryMessage> messages(JsonNode data) {
        JsonNode items = messageItems(data);
        List<MemoryMessage> result = new ArrayList<>();
        if (!items.isArray()) {
            return result;
        }
        for (JsonNode item : items) {
            if (item != null && item.isObject()) {
                result.add(message(item, null));
                JsonNode extracted = item.get("extract");
                if (extracted != null && extracted.isArray()) {
                    extracted.forEach(child -> {
                        if (child != null && child.isObject()) {
                            result.add(message(child, item));
                        }
                    });
                }
            }
        }
        return result;
    }

    public long total(JsonNode data, int fallback) {
        JsonNode container = data;
        if (container != null && container.isObject() && container.get("messages") != null) {
            container = container.get("messages");
        }
        if (container != null && container.isObject()) {
            for (String field : List.of("total_count", "total", "count")) {
                JsonNode value = container.get(field);
                if (value != null && value.canConvertToLong()) {
                    return value.longValue();
                }
            }
        }
        return fallback;
    }

    private MemoryMessage message(JsonNode item, JsonNode parent) {
        MemoryMessage result = new MemoryMessage();
        result.setMemoryId(firstText(item, parent, "memory_id", "memoryId"));
        result.setMessageId(firstText(item, "message_id", "messageId", "id"));
        result.setExternalId(firstText(item, parent, "external_id", "externalId"));
        result.setMemoryType(memoryType(firstText(item, "message_type", "memory_type", "type")));
        result.setContent(firstText(item, "content", "text"));
        result.setSimilarity(doubleField(item, "similarity", "score"));
        result.setEnabled(firstNonNull(booleanField(item, "status", "enabled"),
                parent == null ? null : booleanField(parent, "status", "enabled")));
        result.setAgentId(firstText(item, parent, "agent_id", "agentId"));
        result.setSessionId(firstText(item, parent, "session_id", "sessionId"));
        result.setUserId(firstText(item, parent, "user_id", "userId"));
        result.setSourceId(firstText(item, parent, "source_id", "sourceId"));
        result.setProcessingStatus(firstNonNull(processingStatus(item.get("task")),
                parent == null ? null : processingStatus(parent.get("task"))));
        result.setCreatedAt(firstNonNull(instantField(item, "create_time", "created_at", "createdAt", "valid_at"),
                parent == null ? null : instantField(parent, "create_time", "created_at", "createdAt", "valid_at")));
        return result;
    }

    private JsonNode messageItems(JsonNode data) {
        if (data == null || data.isNull()) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        }
        if (data.isArray()) {
            return data;
        }
        JsonNode nestedMessages = data.get("messages");
        if (nestedMessages != null && nestedMessages.isObject()) {
            JsonNode nested = messageItems(nestedMessages);
            if (nested.isArray()) {
                return nested;
            }
        }
        for (String field : List.of("message_list", "messages", "items", "list")) {
            JsonNode value = data.get(field);
            if (value != null && value.isArray()) {
                return value;
            }
        }
        return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
    }

    private String firstText(JsonNode item, JsonNode parent, String... fields) {
        String value = firstText(item, fields);
        return StringUtils.hasText(value) || parent == null ? value : firstText(parent, fields);
    }

    private <T> T firstNonNull(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private List<MemoryType> memoryTypes(JsonNode value) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        List<MemoryType> result = new ArrayList<>();
        if (value.isArray()) {
            value.forEach(item -> addMemoryType(result, item.asText(null)));
        } else {
            for (String item : value.asText("").split(",")) {
                addMemoryType(result, item);
            }
        }
        return result;
    }

    private void addMemoryType(List<MemoryType> result, String value) {
        MemoryType type = memoryType(value);
        if (type != null && !result.contains(type)) {
            result.add(type);
        }
    }

    private MemoryType memoryType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return MemoryType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String processingStatus(JsonNode task) {
        if (task == null || task.isNull()) {
            return null;
        }
        String status = firstText(task, "status", "state", "progress_msg");
        if (StringUtils.hasText(status)) {
            return status;
        }
        JsonNode progress = task.get("progress");
        if (progress != null && progress.isNumber()) {
            return progress.asDouble() >= 1D ? "COMPLETED" : "PROCESSING";
        }
        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isValueNode() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private Integer integerField(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.canConvertToInt()) {
                return value.intValue();
            }
        }
        return null;
    }

    private Double doubleField(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isNumber()) {
                return value.doubleValue();
            }
        }
        return null;
    }

    private Boolean booleanField(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isBoolean()) {
                return value.booleanValue();
            }
            if (value.isNumber()) {
                return value.intValue() != 0;
            }
            if (value.isTextual()) {
                return Boolean.parseBoolean(value.asText());
            }
        }
        return null;
    }

    private Instant instantField(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                long epoch = value.longValue();
                return epoch > 10_000_000_000L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
            }
            if (value.isTextual()) {
                try {
                    return Instant.parse(value.asText());
                } catch (DateTimeParseException ignored) {
                    // Older deployments may return a local timestamp. It remains optional in the neutral contract.
                }
            }
        }
        return null;
    }
}
