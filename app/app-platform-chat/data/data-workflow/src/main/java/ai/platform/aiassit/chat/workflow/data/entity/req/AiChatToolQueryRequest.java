package ai.platform.aiassit.chat.workflow.data.entity.req;

import ai.platform.aiassit.chat.workflow.data.enums.AiChatToolSyncStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatToolQueryRequest extends BaseRequest {

    private String code;

    private String name;

    private String runtimeType;

    private AiChatToolSyncStatus syncStatus;

    private Boolean enabled;
}
