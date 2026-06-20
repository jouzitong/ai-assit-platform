package ai.platform.aiassist.service.ai.kb.entity.req;

import ai.platform.aiassist.service.ai.api.enums.AiKbVersionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbVersionQueryRequest extends BaseRequest {

    private Long id;

    private String kbCode;

    private Integer versionNo;

    private AiKbVersionStatus status;

    @IgnoredQuery
    private Boolean orderByVersionNoDesc;
}
