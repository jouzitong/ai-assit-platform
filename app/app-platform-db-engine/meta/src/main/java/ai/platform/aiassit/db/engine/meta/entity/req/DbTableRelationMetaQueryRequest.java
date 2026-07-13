package ai.platform.aiassit.db.engine.meta.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbTableRelationMetaQueryRequest extends BaseRequest {

    private String sourceKey;

    private String relationName;

    private String sourceTableName;

    private String targetTableName;

    private Boolean enabled;
}
