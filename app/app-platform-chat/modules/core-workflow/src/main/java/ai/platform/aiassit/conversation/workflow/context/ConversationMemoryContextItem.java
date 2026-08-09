package ai.platform.aiassit.conversation.workflow.context;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** One transient, provider-id-free memory item safe to pass as untrusted Agent business data. */
@Data
public class ConversationMemoryContextItem implements Serializable {
    private String scope;
    private String memoryType;
    private String content;
    private String sourceSessionCode;
    private Instant createdAt;

    public Map<String, Object> toAgentData() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", scope);
        result.put("memoryType", memoryType);
        result.put("content", content);
        if (sourceSessionCode != null && !sourceSessionCode.isBlank()) {
            result.put("sourceSessionCode", sourceSessionCode);
        }
        if (createdAt != null) {
            result.put("createdAt", createdAt.toString());
        }
        return result;
    }
}
