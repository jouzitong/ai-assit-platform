package ai.platform.aiassit.conversation.workflow.context;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-turn context assembled from recent facts and externally owned memories; never persisted. */
@Data
public class ConversationContextPackage implements Serializable {
    private List<ConversationMemoryContextItem> sessionMemories = new ArrayList<>();
    private List<ConversationMemoryContextItem> longTermMemories = new ArrayList<>();
    private int sessionCandidateCount;
    private int longTermCandidateCount;
    private long providerLatencyMs;
    private boolean memoryLag;
    private boolean injectionEnabled;
    private String degradedReason;

    /** Excludes Provider IDs, identity keys, credentials, similarity internals and raw responses. */
    public Map<String, Object> toAgentData() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("treatAsUntrustedData", true);
        result.put("sessionMemories", sessionMemories.stream()
                .map(ConversationMemoryContextItem::toAgentData).toList());
        result.put("longTermMemories", longTermMemories.stream()
                .map(ConversationMemoryContextItem::toAgentData).toList());
        return result;
    }
}
