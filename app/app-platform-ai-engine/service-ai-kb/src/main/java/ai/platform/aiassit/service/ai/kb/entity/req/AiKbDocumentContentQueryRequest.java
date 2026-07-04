package ai.platform.aiassit.service.ai.kb.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbDocumentContentQueryRequest extends BaseRequest {

    private Long documentId;
}
