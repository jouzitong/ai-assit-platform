package ai.platform.aiassit.db.engine.meta.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbDataSourceQueryRequest extends BaseRequest {

    private String keyword;

    private String sourceKey;

    private String sourceType;

    private String status;

    private String ownerTeam;

    private Boolean enabled;
}
