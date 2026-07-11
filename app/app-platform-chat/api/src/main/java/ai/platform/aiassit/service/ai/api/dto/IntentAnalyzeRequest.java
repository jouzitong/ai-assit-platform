package ai.platform.aiassit.service.ai.api.dto;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class IntentAnalyzeRequest implements Serializable {

    private AiChatClientType clientType;

    private String model;

    private String scene;

    private String query;

    private List<ChatMessage> history = new ArrayList<>();

    private String retrievalContext;

    private RequestMeta meta = new RequestMeta();

    private Map<String, Object> ext = new HashMap<>();
}
