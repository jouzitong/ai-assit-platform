package ai.platform.aiassit.chat.history.entity.req;

import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatHistoryQueryRequest extends BaseRequest {

    private String sessionCode;

    private String roundCode;

    private Long createdBy;

    private String role;

    private String roundType;

    private String messageType;

    private String artifactType;

    private String stage;

    private Boolean visibleFlag;

    private AiChatBusinessType businessType;
}
