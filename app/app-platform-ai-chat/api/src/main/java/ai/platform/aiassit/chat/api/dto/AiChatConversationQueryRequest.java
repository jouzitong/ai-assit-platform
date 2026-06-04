package ai.platform.aiassit.chat.api.dto;

import ai.platform.aiassit.chat.history.entity.enums.AiChatBusinessType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatConversationQueryRequest extends BaseRequest {

    private Long userId;

    private String sessionCode;

    private AiChatBusinessType businessType;
}
