package ai.platform.aiassit.render.data.component.entity.req;

import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class RenderComponentManageQueryRequest extends BaseRequest {

    @IgnoredQuery
    private String keyword;

    private String category;

    private EffectiveStatus status;
}
