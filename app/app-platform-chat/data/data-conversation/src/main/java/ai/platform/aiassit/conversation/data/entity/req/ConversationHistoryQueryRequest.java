package ai.platform.aiassit.conversation.data.entity.req;

import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationHistoryQueryRequest extends BaseRequest {

    private String sessionCode;

    private String roundCode;

    @Deprecated
    private Long createdBy;

    @IgnoredQuery
    private Long userId;

    /** 仅由 ConversationSessionService 显式追加，不能污染 round/message 等共享查询。 */
    @IgnoredQuery
    private String groupCode;

    private String role;

    private String roundType;

    private String messageType;

    private String artifactType;

    private String artifactCode;

    private String stage;

    private String activityCode;

    private String agentCode;

    private String activityType;

    private Boolean visibleFlag;

    private ConversationBusinessType businessType;
}
