package ai.platform.aiassit.service.ai.api.memory.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ConversationMemoryCounts implements Serializable {
    private int sessionMemories;
    private int longTermMemories;
    private int processing;
    private int disabled;
}
