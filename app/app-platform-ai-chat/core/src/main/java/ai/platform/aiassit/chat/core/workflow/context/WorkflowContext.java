package ai.platform.aiassit.chat.core.workflow.context;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Data
public class WorkflowContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private AiChatQueryCommand command;

    private String workflowCode;

    private Map<String, Object> data = new HashMap<>();

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public <T> T get(String key) {
        return (T) data.get(key);
    }


}
