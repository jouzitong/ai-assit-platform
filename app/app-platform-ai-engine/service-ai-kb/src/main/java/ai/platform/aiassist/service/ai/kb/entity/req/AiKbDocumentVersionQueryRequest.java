package ai.platform.aiassist.service.ai.kb.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbDocumentVersionQueryRequest extends BaseRequest {

    private String kbCode;

    private Long kbVersionId;

    private String documentCode;
}
