package ai.platform.aiassist.service.ai.kb.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbStoreQueryRequest extends BaseRequest {

    private String kbCode;

    private String kbName;

    @IgnoredQuery
    private Boolean enabled;

    @IgnoredQuery
    private String keyword;
}
