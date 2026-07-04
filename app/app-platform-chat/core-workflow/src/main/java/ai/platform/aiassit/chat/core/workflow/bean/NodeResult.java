package ai.platform.aiassit.chat.core.workflow.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean success;

    private String nextNodeId;

    private String errorMessage;

    public static NodeResult success(String nextNodeId) {
        NodeResult result = new NodeResult();
        result.success = true;
        result.nextNodeId = nextNodeId;
        return result;
    }

    public static NodeResult fail(String errorMessage) {
        NodeResult result = new NodeResult();
        result.success = false;
        result.errorMessage = errorMessage;
        return result;
    }

}
