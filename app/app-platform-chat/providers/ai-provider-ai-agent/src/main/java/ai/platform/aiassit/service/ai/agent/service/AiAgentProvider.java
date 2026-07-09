package ai.platform.aiassit.service.ai.agent.service;

import ai.platform.aiassit.service.ai.agent.config.AiAgentProperties;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.dto.OutputItem;
import ai.platform.aiassit.service.ai.api.dto.Usage;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.enums.FinishReason;
import ai.platform.aiassit.service.ai.api.enums.OutputType;
import ai.platform.aiassit.service.ai.api.enums.ProviderType;
import ai.platform.aiassit.service.ai.api.stream.ChatChunk;
import ai.platform.aiassit.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassit.service.ai.spi.AiChatService;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.provider.ai-agent", name = "enabled", havingValue = "true")
public class AiAgentProvider implements AiChatService {

    private final AiAgentProperties properties;
    private final AiAgentProcessExecutor processExecutor;

    public AiAgentProvider(AiAgentProperties properties, AiAgentProcessExecutor processExecutor) {
        this.properties = properties;
        this.processExecutor = processExecutor;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.AI_AGENT;
    }

    @Override
    public ChatResponse chat(ProviderChatRequest request) {
        log.debug("ai agent chat request: {}", request);
        JsonNode responseNode = processExecutor.execute(properties, request);
        return toChatResponse(responseNode);
    }

    @Override
    public void chatStream(ProviderChatRequest request, ChatStreamObserver observer) {
        if (observer == null) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_OBSERVER);
        }
        try {
            ChatResponse response = chat(request);
            String text = extractText(response);
            if (StringUtils.hasText(text)) {
                ChatChunk chunk = new ChatChunk();
                chunk.setRequestId(response.getRequestId());
                chunk.setOutputType(OutputType.TEXT);
                chunk.setDelta(text);
                observer.onChunk(chunk);
            }
            observer.onComplete();
        } catch (Exception ex) {
            observer.onError(ex);
        }
    }

    private ChatResponse toChatResponse(JsonNode node) {
        ChatResponse response = new ChatResponse();
        response.setRequestId(text(node, "requestId"));
        response.setModel(text(node, "model"));
        response.setFinishReason(resolveFinishReason(text(node, "finishReason")));

        JsonNode outputsNode = node.get("outputs");
        if (outputsNode != null && outputsNode.isArray()) {
            for (JsonNode itemNode : outputsNode) {
                OutputItem item = new OutputItem();
                String type = text(itemNode, "type");
                item.setType(resolveOutputType(type));
                item.setText(text(itemNode, "text"));
                JsonNode jsonNode = itemNode.get("json");
                if (jsonNode != null && jsonNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        item.getJson().put(entry.getKey(), simpleValue(entry.getValue()));
                    }
                }
                response.getOutputs().add(item);
            }
        }

        JsonNode usageNode = node.get("usage");
        if (usageNode != null && usageNode.isObject()) {
            Usage usage = new Usage();
            usage.setInputTokens(intValue(usageNode.get("inputTokens")));
            usage.setOutputTokens(intValue(usageNode.get("outputTokens")));
            usage.setTotalTokens(intValue(usageNode.get("totalTokens")));
            response.setUsage(usage);
        }

        JsonNode providerMetaNode = node.get("providerMeta");
        if (providerMetaNode != null && providerMetaNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = providerMetaNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                response.getProviderMeta().put(entry.getKey(), simpleValue(entry.getValue()));
            }
        }

        if (response.getFinishReason() == null) {
            response.setFinishReason(FinishReason.STOP);
        }
        return response;
    }

    private String extractText(ChatResponse response) {
        return response.getOutputs().stream()
                .filter(item -> item.getType() == OutputType.TEXT && StringUtils.hasText(item.getText()))
                .map(OutputItem::getText)
                .findFirst()
                .orElse(null);
    }

    private OutputType resolveOutputType(String type) {
        if (!StringUtils.hasText(type)) {
            return OutputType.TEXT;
        }
        try {
            return OutputType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return OutputType.TEXT;
        }
    }

    private FinishReason resolveFinishReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return FinishReason.STOP;
        }
        try {
            return FinishReason.valueOf(reason.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FinishReason.STOP;
        }
    }

    private Object simpleValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) {
            return node.asDouble();
        }
        if (node.isObject() || node.isArray()) {
            return node;
        }
        return node.asText();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private int intValue(JsonNode node) {
        return node == null || node.isNull() ? 0 : node.asInt();
    }
}
