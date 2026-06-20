package ai.platform.aiassist.service.ai.kb.entity.req;

import ai.platform.aiassist.service.ai.api.enums.AiKbVersionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbVersionQueryRequest extends BaseRequest {

    private String kbCode;

    private AiKbVersionStatus status;

    private Boolean orderByVersionNoDesc;
}
