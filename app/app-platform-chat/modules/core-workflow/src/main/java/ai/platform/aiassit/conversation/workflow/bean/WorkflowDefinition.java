package ai.platform.aiassit.conversation.workflow.bean;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Data
@NoArgsConstructor
public class WorkflowDefinition implements Serializable {

    private String workflowCode;

    private String version = "1.0";

    private Map<String, WorkflowNodeConfig> nodes;

    private String startNodeId;

    private List<WorkflowTransitionEdge> transitions = new ArrayList<>();

    private WorkflowPolicy policy = WorkflowPolicy.defaultPolicy();

    public WorkflowDefinition(String workflowCode,
                              Map<String, WorkflowNodeConfig> nodes,
                              String startNodeId) {
        this.workflowCode = workflowCode;
        this.nodes = nodes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(nodes);
        this.startNodeId = startNodeId;
    }

    public WorkflowDefinition(String workflowCode,
                              String version,
                              Map<String, WorkflowNodeConfig> nodes,
                              String startNodeId,
                              List<WorkflowTransitionEdge> transitions,
                              WorkflowPolicy policy) {
        this.workflowCode = workflowCode;
        this.version = version;
        this.nodes = nodes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(nodes);
        this.startNodeId = startNodeId;
        this.transitions = transitions == null ? new ArrayList<>() : new ArrayList<>(transitions);
        this.policy = policy == null ? WorkflowPolicy.defaultPolicy() : policy;
    }

}
