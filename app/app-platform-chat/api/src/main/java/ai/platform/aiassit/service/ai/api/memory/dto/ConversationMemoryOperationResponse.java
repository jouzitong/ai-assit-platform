package ai.platform.aiassit.service.ai.api.memory.dto;

import ai.platform.aiassit.service.ai.api.memory.enums.MemoryItemStatus;
import lombok.Data;

import java.io.Serializable;

@Data
public class ConversationMemoryOperationResponse implements Serializable {
    private String memoryRef;
    private MemoryItemStatus status;
    private boolean accepted;

    public static ConversationMemoryOperationResponse accepted(String memoryRef, MemoryItemStatus status) {
        ConversationMemoryOperationResponse response = new ConversationMemoryOperationResponse();
        response.setMemoryRef(memoryRef);
        response.setStatus(status);
        response.setAccepted(true);
        return response;
    }
}
