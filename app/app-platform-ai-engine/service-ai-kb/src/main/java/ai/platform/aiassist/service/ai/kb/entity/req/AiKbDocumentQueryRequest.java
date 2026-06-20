package ai.platform.aiassist.service.ai.kb.entity.req;

import ai.platform.aiassist.service.ai.api.enums.AiKbDocumentStatus;
import ai.platform.aiassist.service.ai.api.enums.AiKbReviewStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbDocumentQueryRequest extends BaseRequest {

    private String kbCode;

    private String documentCode;

    private Long kbVersionId;

    private AiKbDocumentStatus status;

    private AiKbReviewStatus reviewStatus;
}
