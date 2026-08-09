package ai.platform.aiassit.service.ai.api.memory.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class ConversationMemoryListResponse implements Serializable {
    private Instant generatedAt;
    private String providerStatus;
    private boolean memoryLag;
    private List<ConversationMemoryItem> items = new ArrayList<>();
    private List<ConversationMemoryItem> processingItems = new ArrayList<>();
}
