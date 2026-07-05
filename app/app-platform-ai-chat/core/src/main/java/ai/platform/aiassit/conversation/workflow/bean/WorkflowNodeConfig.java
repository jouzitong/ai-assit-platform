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
public class WorkflowNodeConfig implements Serializable {

    private String nodeId;

    private String nextNodeId;

    private List<WorkflowNodeSkillConfig> skills = new ArrayList<>();

    private List<WorkflowNodeCapabilityConfig> capabilities = new ArrayList<>();

    private Map<String, Object> options = new LinkedHashMap<>();

    private Map<String, Object> ext = new LinkedHashMap<>();

    public WorkflowNodeConfig(String nodeId,
                              String nextNodeId,
                              List<WorkflowNodeSkillConfig> skills) {
        this.nodeId = nodeId;
        this.nextNodeId = nextNodeId;
        this.skills = skills == null ? new ArrayList<>() : new ArrayList<>(skills);
        this.capabilities = new ArrayList<>();
    }

    public WorkflowNodeConfig(String nodeId,
                              String nextNodeId,
                              List<WorkflowNodeSkillConfig> skills,
                              List<WorkflowNodeCapabilityConfig> capabilities) {
        this.nodeId = nodeId;
        this.nextNodeId = nextNodeId;
        this.skills = skills == null ? new ArrayList<>() : new ArrayList<>(skills);
        this.capabilities = capabilities == null ? new ArrayList<>() : new ArrayList<>(capabilities);
    }

}
