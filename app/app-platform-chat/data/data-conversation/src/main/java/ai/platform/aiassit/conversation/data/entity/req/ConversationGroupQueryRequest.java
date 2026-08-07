package ai.platform.aiassit.conversation.data.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

/** 分组查询条件。用户归属和分组编码由分组 service 显式加入查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationGroupQueryRequest extends BaseRequest {

    @IgnoredQuery
    private Long userId;

    @IgnoredQuery
    private String groupCode;
}
