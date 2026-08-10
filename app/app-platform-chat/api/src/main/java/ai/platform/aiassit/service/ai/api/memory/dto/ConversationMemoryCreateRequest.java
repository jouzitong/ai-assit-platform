package ai.platform.aiassit.service.ai.api.memory.dto;

import lombok.Data;

import java.io.Serializable;

/** Request for a user-confirmed, manually entered long-term Memory. */
@Data
public class ConversationMemoryCreateRequest implements Serializable {
    private String content;
    private Boolean confirmed;
}
