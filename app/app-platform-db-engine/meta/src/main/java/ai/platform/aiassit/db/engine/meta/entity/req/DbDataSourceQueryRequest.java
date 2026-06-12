package ai.platform.aiassit.db.engine.meta.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbDataSourceQueryRequest extends BaseRequest {

    @IgnoredQuery
    private String keyword;

    private String sourceKey;

    private String sourceType;

    private String status;

    private String ownerTeam;

    private Boolean enabled;
}
