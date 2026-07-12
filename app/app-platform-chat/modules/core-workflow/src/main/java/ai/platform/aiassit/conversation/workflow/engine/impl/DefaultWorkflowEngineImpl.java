package ai.platform.aiassit.conversation.workflow.engine.impl;

import ai.platform.aiassit.conversation.constant.ConversationEventPhases;
import ai.platform.aiassit.conversation.constant.ConversationEventSources;
import ai.platform.aiassit.conversation.constant.ConversationEventTypes;
import ai.platform.aiassit.conversation.workflow.bean.DecisionSource;
import ai.platform.aiassit.conversation.workflow.bean.NodeExecutionResult;
import ai.platform.aiassit.conversation.workflow.bean.NodeResult;
import ai.platform.aiassit.conversation.workflow.bean.TransitionAction;
import ai.platform.aiassit.conversation.workflow.bean.TransitionDecision;
import ai.platform.aiassit.conversation.workflow.bean.TransitionProposal;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowExecutionState;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.constants.ConversationRuntimeContextKeys;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.conversation.workflow.context.WorkflowNodeResult;
import ai.platform.aiassit.conversation.workflow.engine.IWorkflowEngine;
import ai.platform.aiassit.conversation.workflow.engine.transition.TransitionResolver;
import ai.platform.aiassit.conversation.workflow.node.IWorkflowNode;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationCancelledException;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DefaultWorkflowEngineImpl implements IWorkflowEngine {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final Map<String, IWorkflowNode> nodeRegistry;
    private final AiChatRoundService roundService;
    private final TransitionResolver transitionResolver;

    public DefaultWorkflowEngineImpl(List<IWorkflowNode> nodes,
                                     AiChatRoundService roundService,
                                     TransitionResolver transitionResolver) {
        nodeRegistry = new HashMap<>();
        for (IWorkflowNode node : nodes) {
            nodeRegistry.put(node.code(), node);
        }
        this.roundService = roundService;
        this.transitionResolver = transitionResolver;
    }

    @Override
    public void run(ConversationRuntimeContext context) {
        executeWorkflow(context);
    }

    private void executeWorkflow(ConversationRuntimeContext context) {
        try {
            WorkflowDefinition definition = context.getWorkflowDefinition();
            if (definition == null) {
                failWorkflow(context, "workflow definition is required", "workflow-error", "workflow definition is required");
                return;
            }
            log.info("开始执行对话工作流，context={}", context);
            WorkflowExecutionState state = createExecutionState(definition);
            String currentNodeId = definition.getStartNodeId();
            while (currentNodeId != null) {
                context.checkCancellation();
                state.setCurrentNodeId(currentNodeId);
                state.setTotalSteps(state.getTotalSteps() + 1);
                state.incrementNodeAttempt(currentNodeId);
                WorkflowNodeConfig workflowNodeConfig = definition.getNodes().get(currentNodeId);
                if (workflowNodeConfig == null) {
                    failWorkflow(context,
                            "workflow node config not found: " + currentNodeId,
                            ConversationEventTypes.ERROR,
                            "workflow node config not found: " + currentNodeId);
                    return;
                }
                IWorkflowNode currentNode = nodeRegistry.get(workflowNodeConfig.getNodeId());
                if (currentNode == null) {
                    failWorkflow(context,
                            "workflow node not found: " + workflowNodeConfig.getNodeId(),
                            ConversationEventTypes.ERROR,
                            "workflow node not found: " + workflowNodeConfig.getNodeId());
                    return;
                }
                context.publishProgressEvent(
                        ConversationEventSources.WORKFLOW,
                        ConversationEventPhases.STARTED,
                        "start node: " + workflowNodeConfig.getNodeId(),
                        Map.of("nodeCode", workflowNodeConfig.getNodeId())
                );
                long nodeStartedAt = System.currentTimeMillis();
                log.info("工作流节点开始执行，context={}, nodeCode={}, step={}", context,
                        workflowNodeConfig.getNodeId(), state.getTotalSteps());
                NodeExecutionResult result;
                try {
                    result = adaptNodeResult(currentNode.execute(context, workflowNodeConfig), workflowNodeConfig);
                } catch (ConversationCancelledException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("工作流节点执行异常，context={}, nodeCode={}, durationMs={}", context,
                            workflowNodeConfig.getNodeId(), System.currentTimeMillis() - nodeStartedAt, e);
                    failWorkflow(context,
                            "Error executing node: " + workflowNodeConfig.getNodeId() + ", error=" + e.getMessage(),
                            ConversationEventTypes.ERROR,
                            "node failed: " + workflowNodeConfig.getNodeId() + ", error=" + e.getMessage());
                    return;
                }
                context.checkCancellation();
                if (result == null || !result.isSuccess()) {
                    log.warn("工作流节点返回失败结果，context={}, nodeCode={}, durationMs={}, error={}", context,
                            workflowNodeConfig.getNodeId(), System.currentTimeMillis() - nodeStartedAt,
                            result == null ? "node result is null" : result.getErrorMessage());
                    failWorkflow(context,
                            result == null ? "node result is null" : result.getErrorMessage(),
                            ConversationEventTypes.ERROR,
                            "node failed: " + workflowNodeConfig.getNodeId() + ", error=" + (result == null ? "node result is null" : result.getErrorMessage()));
                    return;
                }
                context.publishProgressEvent(
                        ConversationEventSources.WORKFLOW,
                        ConversationEventPhases.COMPLETED,
                        "complete node: " + workflowNodeConfig.getNodeId(),
                        Map.of("nodeCode", workflowNodeConfig.getNodeId())
                );
                log.info("工作流节点执行完成，context={}, nodeCode={}, durationMs={}", context,
                        workflowNodeConfig.getNodeId(), System.currentTimeMillis() - nodeStartedAt);
                TransitionDecision decision = transitionResolver.resolve(definition, state, workflowNodeConfig, result);
                if (decision == null) {
                    failWorkflow(context,
                            "workflow transition decision is required",
                            ConversationEventTypes.ERROR,
                            "workflow transition decision is required");
                    return;
                }
                publishTransitionDecision(context, workflowNodeConfig, decision);
                log.info("工作流节点流转已决策，context={}, currentNodeCode={}, action={}, targetNodeId={}, reason={}", context,
                        workflowNodeConfig.getNodeId(), decision.getAction(), decision.getTargetNodeId(), decision.getReason());
                if (decision.getAction() == TransitionAction.FAIL) {
                    failWorkflow(context,
                            StringUtils.defaultIfBlank(decision.getReason(), "workflow transition failed"),
                            ConversationEventTypes.ERROR,
                            "workflow transition failed: " + decision.getReason());
                    return;
                }
                if (decision.getAction() == TransitionAction.WAIT || decision.getAction() == TransitionAction.CLARIFY) {
                    if (decision.getAction() == TransitionAction.WAIT) {
                        context.publishProgressEvent(
                                ConversationEventSources.WORKFLOW,
                                ConversationEventPhases.RUNNING,
                                StringUtils.defaultIfBlank(decision.getReason(), "workflow paused")
                        );
                    } else {
                        context.publishClarificationEvent(
                                ConversationEventSources.WORKFLOW,
                                ConversationEventPhases.READY,
                                StringUtils.defaultIfBlank(decision.getReason(), "workflow paused")
                        );
                    }
                    currentNodeId = null;
                    continue;
                }
                if (decision.getAction() == TransitionAction.COMPLETE) {
                    currentNodeId = null;
                    continue;
                }
                currentNodeId = decision.getTargetNodeId();
            }
            finishRound(context, STATUS_SUCCESS);
            log.info("对话工作流执行结束，context={}", context);
        } catch (ConversationCancelledException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("对话工作流在完成前发生未预期异常，context={}", context, ex);
            failWorkflow(context,
                    ex.getMessage(),
                    ConversationEventTypes.ERROR,
                    "workflow execution failed: " + ex.getMessage());
        }
    }

    private void failWorkflow(ConversationRuntimeContext context, String errorMessage, String eventType, String eventMessage) {
        context.put(ConversationRuntimeContextKeys.Common.ERROR, errorMessage);
        if (ConversationEventTypes.ERROR.equals(eventType)) {
            context.publishErrorEvent(ConversationEventSources.WORKFLOW, ConversationEventPhases.FAILED, eventMessage);
        } else {
            context.publishEvent(eventType, ConversationEventSources.WORKFLOW, ConversationEventPhases.FAILED, eventMessage);
        }
        finishRound(context, STATUS_FAILED);
    }

    private void finishRound(ConversationRuntimeContext context, String status) {
        AiChatRoundDTO round = context == null ? null : context.getRound();
        if (round == null || round.getId() == null) {
            return;
        }
        AiChatRoundDTO update = new AiChatRoundDTO();
        update.setStatus(status);
        String actualModel = resolveRoundActualModel(context);
        if (org.springframework.util.StringUtils.hasText(actualModel)) {
            update.setActualModel(actualModel);
            round.setActualModel(actualModel);
        }
        String modelCode = resolveRoundModelCode(context);
        if (org.springframework.util.StringUtils.hasText(modelCode)) {
            update.setModelCode(modelCode);
            round.setModelCode(modelCode);
        }
        roundService.edit(round.getId(), update);
        round.setStatus(status);
    }

    private String resolveRoundActualModel(ConversationRuntimeContext context) {
        WorkflowNodeResult nodeResult = resolveRoundModelNodeResult(context);
        if (nodeResult != null && nodeResult.getResponse() != null
                && org.springframework.util.StringUtils.hasText(nodeResult.getResponse().getModel())) {
            return nodeResult.getResponse().getModel().trim();
        }
        if (nodeResult != null && nodeResult.getRequest() != null
                && org.springframework.util.StringUtils.hasText(nodeResult.getRequest().getModel())) {
            return nodeResult.getRequest().getModel().trim();
        }
        AiChatRoundDTO round = context == null ? null : context.getRound();
        return round == null ? null : round.getActualModel();
    }

    private String resolveRoundModelCode(ConversationRuntimeContext context) {
        WorkflowNodeResult nodeResult = resolveRoundModelNodeResult(context);
        if (nodeResult != null && nodeResult.getRequest() != null
                && org.springframework.util.StringUtils.hasText(nodeResult.getRequest().getModel())) {
            return nodeResult.getRequest().getModel().trim();
        }
        if (nodeResult != null && nodeResult.getResponse() != null
                && org.springframework.util.StringUtils.hasText(nodeResult.getResponse().getModel())) {
            return nodeResult.getResponse().getModel().trim();
        }
        AiChatRoundDTO round = context == null ? null : context.getRound();
        return round == null ? null : round.getModelCode();
    }

    private WorkflowNodeResult resolveRoundModelNodeResult(ConversationRuntimeContext context) {
        if (context == null || context.getOrCreateResultContext().getNodeResults() == null) {
            return null;
        }
        List<String> candidateNodeCodes = List.of(
                WorkflowNodeCodes.RENDER.getNodeCode(),
                WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode(),
                WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(),
                WorkflowNodeCodes.QUERY_PLANNING.getNodeCode()
        );
        for (String nodeCode : candidateNodeCodes) {
            WorkflowNodeResult nodeResult = context.getOrCreateResultContext().getNodeResults().get(nodeCode);
            if (nodeResult == null) {
                continue;
            }
            if (nodeResult.getResponse() != null && org.springframework.util.StringUtils.hasText(nodeResult.getResponse().getModel())) {
                return nodeResult;
            }
            if (nodeResult.getRequest() != null && org.springframework.util.StringUtils.hasText(nodeResult.getRequest().getModel())) {
                return nodeResult;
            }
        }
        return null;
    }

    private WorkflowExecutionState createExecutionState(WorkflowDefinition definition) {
        WorkflowExecutionState state = new WorkflowExecutionState();
        state.setWorkflowCode(definition == null ? null : definition.getWorkflowCode());
        return state;
    }

    private NodeExecutionResult adaptNodeResult(NodeResult nodeResult, WorkflowNodeConfig workflowNodeConfig) {
        String nodeId = workflowNodeConfig == null ? null : workflowNodeConfig.getNodeId();
        if (nodeResult == null) {
            return NodeExecutionResult.fail(nodeId, "node result is null");
        }
        if (!nodeResult.isSuccess()) {
            return NodeExecutionResult.fail(nodeId, nodeResult.getErrorMessage());
        }
        NodeExecutionResult result = NodeExecutionResult.success(nodeId, STATUS_SUCCESS);
        result.setSummary("node execute success");
        TransitionProposal proposal = new TransitionProposal();
        if (StringUtils.isNotBlank(nodeResult.getNextNodeId())) {
            proposal.setAction(TransitionAction.GOTO);
            proposal.setTargetNodeId(nodeResult.getNextNodeId());
            proposal.setReasonCode("LEGACY_NEXT_NODE");
            proposal.setReason("use legacy node result nextNodeId");
        } else {
            proposal.setAction(TransitionAction.CONTINUE);
            proposal.setTargetNodeId(workflowNodeConfig == null ? null : workflowNodeConfig.getNextNodeId());
            proposal.setReasonCode("LEGACY_DEFAULT_NEXT");
            proposal.setReason("use workflow node default nextNodeId");
        }
        result.setTransitionProposal(proposal);
        result.getMetadata().put("legacyNodeResult", true);
        return result;
    }

    private void publishTransitionDecision(ConversationRuntimeContext context,
                                           WorkflowNodeConfig workflowNodeConfig,
                                           TransitionDecision decision) {
        if (context == null || workflowNodeConfig == null || decision == null) {
            return;
        }
        String source = decision.getDecisionSource() == null ? DecisionSource.DEFAULT_EDGE.name() : decision.getDecisionSource().name();
        String targetNodeId = StringUtils.defaultIfBlank(decision.getTargetNodeId(), "END");
        String reason = StringUtils.defaultIfBlank(decision.getReason(), "no reason");
        context.publishProgressEvent(
                ConversationEventSources.WORKFLOW,
                ConversationEventPhases.READY,
                "transition node: " + workflowNodeConfig.getNodeId()
                        + " -> " + targetNodeId
                        + ", action=" + decision.getAction()
                        + ", source=" + source
                        + ", reason=" + reason,
                Map.of(
                        "nodeCode", workflowNodeConfig.getNodeId(),
                        "targetNodeId", targetNodeId,
                        "action", decision.getAction().name(),
                        "decisionSource", source
                )
        );
    }
}
