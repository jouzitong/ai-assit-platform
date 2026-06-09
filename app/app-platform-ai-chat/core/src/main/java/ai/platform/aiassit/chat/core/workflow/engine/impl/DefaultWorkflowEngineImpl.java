package ai.platform.aiassit.chat.core.workflow.engine.impl;

import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.engine.IWorkflowEngine;
import ai.platform.aiassit.chat.core.workflow.node.IWorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Service
@Slf4j
public class DefaultWorkflowEngineImpl implements IWorkflowEngine {

    private final Map<String, IWorkflowNode> nodeRegistry;

    public DefaultWorkflowEngineImpl(List<IWorkflowNode> nodes) {
        nodeRegistry = new HashMap<>();
        for (IWorkflowNode node : nodes) {
            nodeRegistry.put(node.type(), node);
        }
    }

    @Override
    public void run(WorkflowDefinition definition, WorkflowContext context) {
        String currentNodeId = definition.getStartNodeId();
        while (currentNodeId != null) {
            WorkflowNodeConfig workflowNodeConfig = definition.getNodes().get(currentNodeId);
            if (workflowNodeConfig == null) {
                context.put("error", "workflow node config not found: " + currentNodeId);
                return;
            }
            IWorkflowNode currentNode = nodeRegistry.get(workflowNodeConfig.getNodeType());
            if (currentNode == null) {
                context.put("error", "workflow node not found: " + workflowNodeConfig.getNodeType());
                return;
            }
            NodeResult result = currentNode.execute(context, workflowNodeConfig);
            if (!result.isSuccess()) {
                context.put("error", result.getErrorMessage());
                return;
            }
            if (StringUtils.isNotBlank(result.getNextNodeId())) {
                currentNodeId = result.getNextNodeId();
            } else {
                currentNodeId = workflowNodeConfig.getNextNodeId();
            }
        }

    }
}
