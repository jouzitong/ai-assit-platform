package ai.platform.aiassist.service.ai.kb.entity.req;

import ai.platform.aiassist.service.ai.api.enums.AiKbDocumentStatus;
import ai.platform.aiassist.service.ai.api.enums.AiKbBizType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbDocumentQueryRequest extends BaseRequest {

    private Long id;

    private String kbCode;

    @IgnoredQuery
    private String documentCode;

    @IgnoredQuery
    private String keyword;

    private AiKbBizType bizType;

    private AiKbDocumentStatus status;
}
