package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

@Data
public class ConversationDeleteRequest {

    /** 由登录上下文补入，客户端传值不会作为可信租户依据。 */
    private String tenantId;

    private Long userId;

    private String sessionCode;
}
