package ai.platform.aiassit.user.security.management.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class SecRoleQueryRequest extends BaseRequest {

    @IgnoredQuery
    private String keyword;

    private String roleCode;

    private String status;
}
