package ai.platform.aiassit.conversation.dto.chat;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationStreamReconnectRequest extends BaseRequest {

    private String runId;

    private String lastEventId;

    private String sessionCode;

    private String roundCode;
}
