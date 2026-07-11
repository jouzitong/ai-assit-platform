package ai.platform.aiassit.user.security.management.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class SecUserRoleQueryRequest extends BaseRequest {

    @IgnoredQuery
    private String keyword;

    private Long userId;

    private String roleCode;
}
