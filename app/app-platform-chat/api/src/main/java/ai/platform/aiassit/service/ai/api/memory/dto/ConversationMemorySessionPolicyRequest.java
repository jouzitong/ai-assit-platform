package ai.platform.aiassit.service.ai.api.memory.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ConversationMemorySessionPolicyRequest implements Serializable {
    private String sessionCode;
}
