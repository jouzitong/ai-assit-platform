package ai.platform.aiassit.conversation.workflow.context;

import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Current input and bounded conversation history supplied to an Agent runtime. */
@Data
public class UserMessageContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private AiChatMessageDTO currentMessage;
    private List<AiChatMessageDTO> sessionMessages = new ArrayList<>();
}
