package ai.platform.aiassit.conversation.workflow.service;

import ai.platform.aiassit.conversation.workflow.bean.TransitionAction;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeType;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowPolicy;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowTransitionEdge;
import ai.platform.aiassit.conversation.workflow.context.WorkflowNodeCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WorkflowDefinitionFactory {

    public WorkflowDefinition simpleChatWorkflow() {
        Map<String, WorkflowNodeConfig> nodes = new LinkedHashMap<>();
        WorkflowNodeConfig simpleChatNode = new WorkflowNodeConfig(WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode(), null, List.of());
        simpleChatNode.setName("Simple Chat");
        simpleChatNode.setType(WorkflowNodeType.AGENT);
        nodes.put(WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode(), simpleChatNode);
        return new WorkflowDefinition(
                "ai-chat-simple-chat-workflow",
                "1.0",
                nodes,
                WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode(),
                List.of(
                        new WorkflowTransitionEdge(
                                WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode(),
                                null,
                                TransitionAction.COMPLETE,
                                true,
                                null,
                                new LinkedHashMap<>()
                        )
                ),
                WorkflowPolicy.defaultPolicy()
        );
    }

    public WorkflowDefinition queryRenderWorkflow() {
        Map<String, WorkflowNodeConfig> nodes = new LinkedHashMap<>();

        WorkflowNodeConfig queryPlanningNode = new WorkflowNodeConfig(
                WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(),
                WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(),
                List.of()
        );
        queryPlanningNode.setName("Query Planning");
        queryPlanningNode.setType(WorkflowNodeType.AGENT);
        nodes.put(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(), queryPlanningNode);

        WorkflowNodeConfig sqlPreGenerateNode = new WorkflowNodeConfig(
                WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(),
                WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(),
                List.of()
        );
        sqlPreGenerateNode.setName("Sql Pre Generate");
        sqlPreGenerateNode.setType(WorkflowNodeType.AGENT);
        nodes.put(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), sqlPreGenerateNode);

        WorkflowNodeConfig resultEvaluateNode = new WorkflowNodeConfig(
                WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(),
                WorkflowNodeCodes.RENDER.getNodeCode(),
                List.of()
        );
        resultEvaluateNode.setName("Result Evaluate");
        resultEvaluateNode.setType(WorkflowNodeType.HYBRID);
        nodes.put(WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(), resultEvaluateNode);

        WorkflowNodeConfig renderNode = new WorkflowNodeConfig(WorkflowNodeCodes.RENDER.getNodeCode(), null, List.of());
        renderNode.setName("Render");
        renderNode.setType(WorkflowNodeType.HYBRID);
        nodes.put(WorkflowNodeCodes.RENDER.getNodeCode(), renderNode);

        return new WorkflowDefinition(
                "ai-chat-query-render-workflow",
                "1.0",
                nodes,
                WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(),
                List.of(
                        new WorkflowTransitionEdge(
                                WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(),
                                WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(),
                                TransitionAction.CONTINUE,
                                true,
                                null,
                                new LinkedHashMap<>()
                        ),
                        new WorkflowTransitionEdge(
                                WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(),
                                WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(),
                                TransitionAction.CONTINUE,
                                true,
                                null,
                                new LinkedHashMap<>()
                        ),
                        new WorkflowTransitionEdge(
                                WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(),
                                WorkflowNodeCodes.RENDER.getNodeCode(),
                                TransitionAction.CONTINUE,
                                true,
                                null,
                                new LinkedHashMap<>()
                        ),
                        new WorkflowTransitionEdge(
                                WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(),
                                WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(),
                                TransitionAction.GOTO,
                                false,
                                "sql-pre-generate-retry",
                                new LinkedHashMap<>()
                        ),
                        new WorkflowTransitionEdge(
                                WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(),
                                WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(),
                                TransitionAction.GOTO,
                                false,
                                "query-planning-retry",
                                new LinkedHashMap<>()
                        ),
                        new WorkflowTransitionEdge(
                                WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(),
                                null,
                                TransitionAction.CLARIFY,
                                false,
                                "clarify",
                                new LinkedHashMap<>()
                        ),
                        new WorkflowTransitionEdge(
                                WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(),
                                null,
                                TransitionAction.COMPLETE,
                                false,
                                "partial-complete",
                                new LinkedHashMap<>()
                        ),
                        new WorkflowTransitionEdge(
                                WorkflowNodeCodes.RENDER.getNodeCode(),
                                null,
                                TransitionAction.COMPLETE,
                                true,
                                null,
                                new LinkedHashMap<>()
                        )
                ),
                WorkflowPolicy.defaultPolicy()
        );
    }
}
