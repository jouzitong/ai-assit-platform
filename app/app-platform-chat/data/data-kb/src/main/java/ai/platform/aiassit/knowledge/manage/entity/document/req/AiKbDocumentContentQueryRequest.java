package ai.platform.aiassit.knowledge.manage.entity.document.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbDocumentContentQueryRequest extends BaseRequest {

    private Long documentId;
}
