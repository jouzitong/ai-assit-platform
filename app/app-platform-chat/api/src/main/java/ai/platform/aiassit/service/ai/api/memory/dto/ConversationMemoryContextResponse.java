package ai.platform.aiassit.service.ai.api.memory.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class ConversationMemoryContextResponse implements Serializable {
    private String sessionCode;
    private Instant generatedAt;
    private String providerStatus;
    private boolean memoryLag;
    private ConversationMemoryCounts counts = new ConversationMemoryCounts();
    private List<ConversationMemoryItem> sessionMemories = new ArrayList<>();
    private List<ConversationMemoryItem> longTermMemories = new ArrayList<>();
    private List<ConversationMemoryItem> processingMemories = new ArrayList<>();
    private List<ConversationMemoryItem> disabledMemories = new ArrayList<>();
}
