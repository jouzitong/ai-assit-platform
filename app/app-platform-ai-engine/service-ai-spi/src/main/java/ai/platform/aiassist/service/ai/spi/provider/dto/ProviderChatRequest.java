package ai.platform.aiassist.service.ai.spi.provider.dto;

import ai.platform.aiassist.service.ai.api.dto.ChatMessage;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassist.service.ai.api.dto.ResponseFormat;
import ai.platform.aiassist.service.ai.api.dto.ToolDefinition;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ProviderChatRequest {
    private String model;
    private List<ChatMessage> messages = new ArrayList<>();
    private List<ToolDefinition> tools = new ArrayList<>();
    private ResponseFormat responseFormat;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Integer timeoutMs;
    private RequestMeta meta;
    private Map<String, Object> ext = new HashMap<>();
}
