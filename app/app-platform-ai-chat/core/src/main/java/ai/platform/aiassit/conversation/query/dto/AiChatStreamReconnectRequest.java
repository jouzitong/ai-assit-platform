package ai.platform.aiassit.conversation.query.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatStreamReconnectRequest extends BaseRequest {

    private String sessionCode;

    private String roundCode;
}
