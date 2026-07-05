package ai.platform.aiassit.conversation.workflow.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowDefinition implements Serializable {

    private String workflowCode;

    private Map<String, WorkflowNodeConfig> nodes;

    private String startNodeId;

}
