package ai.platform.aiassit.conversation.dto.chat;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationStreamReconnectRequest extends BaseRequest {

    private String runId;

    private String lastEventId;

    private String sessionCode;

    private String roundCode;

    /**
     * 日志摘要：仅保留 SSE 重连定位字段。
     */
    @Override
    public String toString() {
        return "ConversationStreamReconnectRequest{" +
                "runId='" + runId + '\'' +
                ", lastEventId='" + lastEventId + '\'' +
                ", sessionCode='" + sessionCode + '\'' +
                ", roundCode='" + roundCode + '\'' +
                '}';
    }
}
