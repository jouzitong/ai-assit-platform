package ai.platform.aiassit.service.ai.api.memory.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ConversationMemoryConfirmRequest implements Serializable {
    private Boolean confirmed;
}
