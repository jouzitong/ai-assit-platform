package ai.platform.aiassit.conversation.workflow.engine.transition.impl;

import ai.platform.aiassit.conversation.workflow.bean.DecisionSource;
import ai.platform.aiassit.conversation.workflow.bean.NodeExecutionResult;
import ai.platform.aiassit.conversation.workflow.bean.TransitionAction;
import ai.platform.aiassit.conversation.workflow.bean.TransitionDecision;
import ai.platform.aiassit.conversation.workflow.bean.TransitionProposal;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowExecutionState;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowPolicy;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowTransitionEdge;
import ai.platform.aiassit.conversation.workflow.engine.transition.TransitionResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 默认工作流流转决策器。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Component
public class DefaultTransitionResolver implements TransitionResolver {

    @Override
    public TransitionDecision resolve(WorkflowDefinition definition,
                                      WorkflowExecutionState state,
                                      WorkflowNodeConfig currentNode,
                                      NodeExecutionResult result) {
        WorkflowPolicy policy = definition == null || definition.getPolicy() == null
                ? WorkflowPolicy.defaultPolicy()
                : definition.getPolicy();
        TransitionDecision timeoutDecision = resolveTimeoutDecision(policy, state);
        if (timeoutDecision != null) {
            return timeoutDecision;
        }
        if (state != null && state.getTotalSteps() >= policy.getMaxTotalSteps()) {
            return buildDecision(TransitionAction.FAIL, null, false,
                    DecisionSource.WORKFLOW_POLICY, "workflow max steps exceeded", null);
        }
        TransitionProposal proposal = result == null ? null : result.getTransitionProposal();
        if (proposal != null) {
            TransitionDecision proposalDecision = resolveProposal(definition, state, currentNode, policy, proposal);
            if (proposalDecision != null) {
                return proposalDecision;
            }
        }
        if (StringUtils.hasText(currentNode == null ? null : currentNode.getNextNodeId())) {
            return buildDecision(TransitionAction.CONTINUE, currentNode.getNextNodeId(), false,
                    DecisionSource.DEFAULT_EDGE, "fallback to node default nextNodeId", null);
        }
        return buildDecision(TransitionAction.COMPLETE, null, false,
                DecisionSource.DEFAULT_EDGE, "no next node, workflow complete", null);
    }

    private TransitionDecision resolveProposal(WorkflowDefinition definition,
                                               WorkflowExecutionState state,
                                               WorkflowNodeConfig currentNode,
                                               WorkflowPolicy policy,
                                               TransitionProposal proposal) {
        TransitionAction action = proposal.getAction() == null ? TransitionAction.CONTINUE : proposal.getAction();
        if (action == TransitionAction.COMPLETE || action == TransitionAction.WAIT
                || action == TransitionAction.CLARIFY || action == TransitionAction.FAIL) {
            return buildDecision(action, proposal.getTargetNodeId(), true,
                    DecisionSource.NODE_PROPOSAL, proposal.getReason(), proposal.getConfidence());
        }
        if (action == TransitionAction.RETRY) {
            int nextAttempt = state == null || currentNode == null ? 0 : state.getNodeAttempt(currentNode.getNodeId()) + 1;
            if (nextAttempt > policy.getMaxNodeRetries()) {
                return buildDecision(TransitionAction.FAIL, null, false,
                        DecisionSource.WORKFLOW_POLICY, "node retry limit exceeded", proposal.getConfidence());
            }
            return buildDecision(TransitionAction.RETRY, currentNode == null ? null : currentNode.getNodeId(), true,
                    DecisionSource.NODE_PROPOSAL, proposal.getReason(), proposal.getConfidence());
        }
        String targetNodeId = StringUtils.hasText(proposal.getTargetNodeId())
                ? proposal.getTargetNodeId()
                : currentNode == null ? null : currentNode.getNextNodeId();
        if (!isAllowedTransition(definition, currentNode, action, targetNodeId)) {
            return buildDecision(TransitionAction.FAIL, null, false,
                    DecisionSource.SYSTEM_GUARD, "transition not allowed by workflow definition", proposal.getConfidence());
        }
        return buildDecision(action, targetNodeId, true,
                DecisionSource.NODE_PROPOSAL, proposal.getReason(), proposal.getConfidence());
    }

    private TransitionDecision resolveTimeoutDecision(WorkflowPolicy policy, WorkflowExecutionState state) {
        if (policy == null || state == null || state.getStartedAt() == null) {
            return null;
        }
        long elapsedSeconds = Duration.between(state.getStartedAt(), Instant.now()).getSeconds();
        if (elapsedSeconds <= policy.getMaxWorkflowDurationSeconds()) {
            return null;
        }
        return buildDecision(TransitionAction.FAIL, null, false,
                DecisionSource.WORKFLOW_POLICY, "workflow timeout exceeded", null);
    }

    private boolean isAllowedTransition(WorkflowDefinition definition,
                                        WorkflowNodeConfig currentNode,
                                        TransitionAction action,
                                        String targetNodeId) {
        if (currentNode == null) {
            return false;
        }
        List<WorkflowTransitionEdge> transitions = definition == null ? null : definition.getTransitions();
        if (CollectionUtils.isEmpty(transitions)) {
            return StringUtils.hasText(targetNodeId)
                    ? targetNodeId.equals(currentNode.getNextNodeId())
                    : !StringUtils.hasText(currentNode.getNextNodeId());
        }
        return transitions.stream().anyMatch(edge ->
                edge != null && edge.matches(currentNode.getNodeId(), action, targetNodeId));
    }

    private TransitionDecision buildDecision(TransitionAction action,
                                             String targetNodeId,
                                             boolean acceptedProposal,
                                             DecisionSource source,
                                             String reason,
                                             Double confidence) {
        TransitionDecision decision = new TransitionDecision();
        decision.setAction(action);
        decision.setTargetNodeId(targetNodeId);
        decision.setAcceptedProposal(acceptedProposal);
        decision.setDecisionSource(source);
        decision.setReason(reason);
        decision.setConfidence(confidence);
        return decision;
    }
}
