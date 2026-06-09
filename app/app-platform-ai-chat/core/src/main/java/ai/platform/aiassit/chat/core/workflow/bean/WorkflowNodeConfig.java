package ai.platform.aiassit.chat.core.workflow.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowNodeConfig implements Serializable {

    private String nodeId;

    private String nodeType;

    private String nextNodeId;

    private List<WorkflowNodeSkillConfig> skills = new ArrayList<>();

}
