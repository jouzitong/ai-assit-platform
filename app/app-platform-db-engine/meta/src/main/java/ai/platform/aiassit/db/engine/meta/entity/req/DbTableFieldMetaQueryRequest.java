package ai.platform.aiassit.db.engine.meta.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbTableFieldMetaQueryRequest extends BaseRequest {

    private String sourceKey;

    private String tableName;

    private String columnName;

    @IgnoredQuery
    private String keyword;

    private Boolean enabled;
}
