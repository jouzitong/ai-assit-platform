package ai.platform.aiassit.db.engine.meta.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbTableMetaQueryRequest extends BaseRequest {

    private String sourceKey;

    private String keyword;

    private String tableName;

    private String status;

    private Boolean enabled;
}
