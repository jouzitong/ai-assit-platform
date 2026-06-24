package ai.platform.aiassit.render.data.render.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class RenderPageCategoryQueryRequest extends BaseRequest {

    @IgnoredQuery
    private String keyword;

    private String parentCode;

    private Boolean enabled;
}
