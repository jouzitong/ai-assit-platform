package ai.platform.aiassit.conversation.dto.task;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationTaskStopRequest extends BaseRequest {

    private String runId;

    private String sessionCode;

    private String roundCode;
}
