package ai.platform.aiassit.conversation.workflow.runtime;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 节点执行契约。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
public class NodeContract implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String goal;

    private List<String> contextKeys = new ArrayList<>();

    private List<String> toolCodes = new ArrayList<>();

    private String outputType;

    private List<String> requiredOutputFields = new ArrayList<>();

    private List<String> completionChecks = new ArrayList<>();
}
