package ai.platform.aiassit.chat.workflow.data.entity.req;

import ai.platform.aiassit.chat.workflow.data.enums.AiExecuteType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatNodeQueryRequest extends BaseRequest {

    private String code;

    private String name;

    private String desc;

    private AiExecuteType executeType;

    private Boolean enabled;
}
