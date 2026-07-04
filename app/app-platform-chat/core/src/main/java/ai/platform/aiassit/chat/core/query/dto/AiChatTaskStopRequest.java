package ai.platform.aiassit.chat.core.query.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatTaskStopRequest extends BaseRequest {

    private String sessionCode;

    private String roundCode;
}
