package ai.platform.aiassit.conversation.workflow.engine.impl;

import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.conversation.dto.chat.AiChatQueryCommand;
import ai.platform.aiassit.conversation.workflow.dto.AiChatQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.bean.DecisionSource;
import ai.platform.aiassit.conversation.workflow.bean.NodeExecutionResult;
import ai.platform.aiassit.service.ai.api.dto.IntentAnalyzeResponse;
import ai.platform.aiassit.conversation.workflow.bean.NodeResult;
import ai.platform.aiassit.conversation.workflow.bean.TransitionAction;
import ai.platform.aiassit.conversation.workflow.bean.TransitionDecision;
import ai.platform.aiassit.conversation.workflow.bean.TransitionProposal;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowExecutionState;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeType;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowPolicy;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowTransitionEdge;
import ai.platform.aiassit.conversation.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.conversation.workflow.context.WorkflowContext;
import ai.platform.aiassit.conversation.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.conversation.workflow.context.WorkflowNodeResult;
import ai.platform.aiassit.conversation.workflow.engine.IWorkflowEngine;
import ai.platform.aiassit.conversation.workflow.engine.transition.TransitionResolver;
import ai.platform.aiassit.conversation.workflow.node.IWorkflowNode;
import ai.platform.aiassit.conversation.workflow.planning.service.WorkflowIntentAnalyzeService;
import ai.platform.aiassit.conversation.workflow.support.WorkflowHistoryRecorder;
import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.enums.AiChatActorType;
import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import ai.platform.aiassit.chat.history.enums.AiChatDisplayLevel;
import ai.platform.aiassit.chat.history.enums.AiChatMessageType;
import ai.platform.aiassit.chat.history.enums.AiChatRoundType;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.arthena.framework.common.thread.AsyncTaskManager;
import org.arthena.framework.common.constant.ParamBizCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.auth.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Service
@Slf4j
public class DefaultWorkflowEngineImpl implements IWorkflowEngine {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final Map<String, IWorkflowNode> nodeRegistry;
    private final AiChatSessionService sessionService;
    private final AiChatMessageService messageService;
    private final AiChatArtifactService artifactService;
    private final AiChatRoundService roundService;
    private final WorkflowHistoryRecorder historyRecorder;
    private final AsyncTaskManager asyncTaskManager;
    private final WorkflowIntentAnalyzeService workflowIntentAnalyzeService;
    private final TransitionResolver transitionResolver;

    public DefaultWorkflowEngineImpl(List<IWorkflowNode> nodes,
                                     AiChatSessionService sessionService,
                                     AiChatMessageService messageService,
                                     AiChatArtifactService artifactService,
                                     AiChatRoundService roundService,
                                     WorkflowHistoryRecorder historyRecorder,
                                     AsyncTaskManager asyncTaskManager,
                                     WorkflowIntentAnalyzeService workflowIntentAnalyzeService,
                                     TransitionResolver transitionResolver) {
        nodeRegistry = new HashMap<>();
        for (IWorkflowNode node : nodes) {
            nodeRegistry.put(node.code(), node);
        }
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.artifactService = artifactService;
        this.roundService = roundService;
        this.historyRecorder = historyRecorder;
        this.asyncTaskManager = asyncTaskManager;
        this.workflowIntentAnalyzeService = workflowIntentAnalyzeService;
        this.transitionResolver = transitionResolver;
    }

    @Override
    public void run(WorkflowContext context) {
        if (context != null && context.getEmitter() != null) {
            asyncTaskManager.submit(() -> runAsync(context));
            return;
        }
        executeWorkflow(context);
    }

    private void runAsync(WorkflowContext context) {
        try {
            executeWorkflow(context);
            String error = context.get(WorkflowContextKeys.Common.ERROR);
            if (StringUtils.isNotBlank(error)) {
                throw new IllegalStateException(error);
            }
            sendCompleteEvent(context);
            context.getEmitter().complete();
        } catch (Exception ex) {
            log.error("workflow query stream failed", ex);
            sendErrorEvent(context, ex);
            if (context != null && context.getEmitter() != null) {
                context.getEmitter().completeWithError(ex);
            }
        }
    }

    private void executeWorkflow(WorkflowContext context) {
        try {
            prepareConversationContext(context);
            sendInitEvent(context);
            prepareBaseIntentAnalysis(context);

            WorkflowDefinition definition = context.getWorkflowDefinition();
            if (definition == null) {
                failWorkflow(context, "workflow definition is required", "workflow-error", "workflow definition is required");
                return;
            }
            WorkflowExecutionState state = createExecutionState(definition);
            String currentNodeId = definition.getStartNodeId();
            while (currentNodeId != null) {
                state.setCurrentNodeId(currentNodeId);
                state.setTotalSteps(state.getTotalSteps() + 1);
                state.incrementNodeAttempt(currentNodeId);
                WorkflowNodeConfig workflowNodeConfig = definition.getNodes().get(currentNodeId);
                if (workflowNodeConfig == null) {
                    failWorkflow(context,
                            "workflow node config not found: " + currentNodeId,
                            "workflow-error",
                            "workflow node config not found: " + currentNodeId);
                    return;
                }
                IWorkflowNode currentNode = nodeRegistry.get(workflowNodeConfig.getNodeId());
                if (currentNode == null) {
                    failWorkflow(context,
                            "workflow node not found: " + workflowNodeConfig.getNodeId(),
                            "workflow-error",
                            "workflow node not found: " + workflowNodeConfig.getNodeId());
                    return;
                }
                context.publishEvent("node-start",
                        "start node: " + workflowNodeConfig.getNodeId());
                NodeExecutionResult result;
                try {
                    result = adaptNodeResult(currentNode.execute(context, workflowNodeConfig), workflowNodeConfig);
                } catch (Exception e) {
                    log.error("Error executing node: {}. ", workflowNodeConfig.getNodeId(), e);
                    failWorkflow(context,
                            "Error executing node: " + workflowNodeConfig.getNodeId() + ", error=" + e.getMessage(),
                            "node-failed",
                            "node failed: " + workflowNodeConfig.getNodeId() + ", error=" + e.getMessage());
                    return;
                }
                if (result == null || !result.isSuccess()) {
                    failWorkflow(context,
                            result.getErrorMessage(),
                            "node-failed",
                            "node failed: " + workflowNodeConfig.getNodeId() + ", error=" + result.getErrorMessage());
                    return;
                }
                context.publishEvent("node-complete",
                        "complete node: " + workflowNodeConfig.getNodeId());
                TransitionDecision decision = transitionResolver.resolve(definition, state, workflowNodeConfig, result);
                if (decision == null) {
                    failWorkflow(context,
                            "workflow transition decision is required",
                            "workflow-error",
                            "workflow transition decision is required");
                    return;
                }
                publishTransitionDecision(context, workflowNodeConfig, decision);
                if (decision.getAction() == TransitionAction.FAIL) {
                    failWorkflow(context,
                            StringUtils.defaultIfBlank(decision.getReason(), "workflow transition failed"),
                            "workflow-error",
                            "workflow transition failed: " + decision.getReason());
                    return;
                }
                if (decision.getAction() == TransitionAction.WAIT || decision.getAction() == TransitionAction.CLARIFY) {
                    context.publishEvent(decision.getAction() == TransitionAction.WAIT ? "workflow-wait" : "workflow-clarify",
                            StringUtils.defaultIfBlank(decision.getReason(), "workflow paused"));
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
        } catch (Exception ex) {
            log.error("workflow execution failed before completion, sessionCode={}, roundCode={}",
                    context.getSession() == null ? null : context.getSession().getSessionCode(),
                    context.getRound() == null ? null : context.getRound().getRoundCode(),
                    ex);
            failWorkflow(context,
                    ex.getMessage(),
                    "workflow-error",
                    "workflow execution failed: " + ex.getMessage());
        }
    }

    private void failWorkflow(WorkflowContext context, String errorMessage, String eventName, String eventMessage) {
        context.put(WorkflowContextKeys.Common.ERROR, errorMessage);
        context.publishEvent(eventName, eventMessage);
        finishRound(context, STATUS_FAILED);
    }

    private void prepareBaseIntentAnalysis(WorkflowContext context) {
        try {
            IntentAnalyzeResponse existingResponse = context.get(WorkflowContextKeys.Planning.INTENT_ANALYZE_RESPONSE);
            if (existingResponse != null) {
                refreshRoundType(context, existingResponse);
                rebindWorkflowDefinitionByIntent(context, existingResponse);
                return;
            }
            IntentAnalyzeResponse response = workflowIntentAnalyzeService.analyze(context);
            if (response == null) {
                return;
            }
            context.put(WorkflowContextKeys.Planning.INTENT_ANALYZE_RESPONSE, response);
            context.putNodeOutput(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode(), "intentAnalyzeResponse", response);
            refreshRoundType(context, response);
            rebindWorkflowDefinitionByIntent(context, response);
            context.publishEvent("base-intent-analysis-ready", "base intent analysis prepared");
        } catch (Exception ex) {
            log.warn("base intent analyze failed, sessionCode={}, roundCode={}",
                    context.getSession() == null ? null : context.getSession().getSessionCode(),
                    context.getRound() == null ? null : context.getRound().getRoundCode(),
                    ex);
            context.put(WorkflowContextKeys.Planning.INTENT_ANALYZE_ERROR, ex.getMessage());
            context.publishEvent("base-intent-analysis-skipped", "base intent analysis skipped");
        }
    }

    private void rebindWorkflowDefinitionByIntent(WorkflowContext context, IntentAnalyzeResponse response) {
        String intentType = response == null ? null : response.getIntentType();
        if ("SIMPLE_CHAT".equalsIgnoreCase(intentType)) {
            context.setWorkflowDefinition(buildSimpleChatWorkflowDefinition());
            context.setWorkflowCode("ai-chat-simple-chat-workflow");
            return;
        }
        context.setWorkflowDefinition(buildQueryRenderWorkflowDefinition());
        context.setWorkflowCode("ai-chat-query-render-workflow");
    }

    private void refreshRoundType(WorkflowContext context, IntentAnalyzeResponse response) {
        if (hasExplicitRoundType(context == null ? null : context.getCommand())) {
            return;
        }
        AiChatRoundDTO round = context == null ? null : context.getRound();
        if (round == null || round.getId() == null) {
            return;
        }
        AiChatRoundType roundType = AiChatRoundType.fromIntentType(response == null ? null : response.getIntentType());
        if (roundType == round.getRoundType()) {
            return;
        }
        AiChatRoundDTO update = new AiChatRoundDTO();
        update.setRoundType(roundType);
        roundService.edit(round.getId(), update);
        round.setRoundType(roundType);
    }

    private void finishRound(WorkflowContext context, String status) {
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

    private String resolveRoundActualModel(WorkflowContext context) {
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

    private String resolveRoundModelCode(WorkflowContext context) {
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

    private WorkflowNodeResult resolveRoundModelNodeResult(WorkflowContext context) {
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

    private WorkflowDefinition buildSimpleChatWorkflowDefinition() {
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

    private WorkflowDefinition buildQueryRenderWorkflowDefinition() {
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

    private void publishTransitionDecision(WorkflowContext context,
                                           WorkflowNodeConfig workflowNodeConfig,
                                           TransitionDecision decision) {
        if (context == null || workflowNodeConfig == null || decision == null) {
            return;
        }
        String source = decision.getDecisionSource() == null ? DecisionSource.DEFAULT_EDGE.name() : decision.getDecisionSource().name();
        String targetNodeId = StringUtils.defaultIfBlank(decision.getTargetNodeId(), "END");
        String reason = StringUtils.defaultIfBlank(decision.getReason(), "no reason");
        context.publishEvent(
                "transition-decided",
                "transition node: " + workflowNodeConfig.getNodeId()
                        + " -> " + targetNodeId
                        + ", action=" + decision.getAction()
                        + ", source=" + source
                        + ", reason=" + reason
        );
    }

    private void sendCompleteEvent(WorkflowContext context) throws IOException {
        if (context == null || context.getEmitter() == null) {
            return;
        }
        AiChatQueryStreamEvent completeEvent = new AiChatQueryStreamEvent();
        completeEvent.setEventType("complete");
        completeEvent.setRequestId(context.getCommand() == null ? null : context.getCommand().getTraceId());
        completeEvent.setSessionCode(context.getSession() == null ? null : context.getSession().getSessionCode());
        completeEvent.setRoundCode(context.getRound() == null ? null : context.getRound().getRoundCode());
        completeEvent.setAnswer(context.getRenderedAnswer());
        completeEvent.setStatus(STATUS_SUCCESS);
        context.getEmitter().send(SseEmitter.event().name("complete").data(completeEvent, MediaType.APPLICATION_JSON));
    }

    private void sendErrorEvent(WorkflowContext context, Exception ex) {
        if (context == null || context.getEmitter() == null) {
            return;
        }
        AiChatQueryStreamEvent errorEvent = new AiChatQueryStreamEvent();
        errorEvent.setEventType("error");
        errorEvent.setRequestId(context.getCommand() == null ? null : context.getCommand().getTraceId());
        errorEvent.setSessionCode(context.getSession() == null ? null : context.getSession().getSessionCode());
        errorEvent.setRoundCode(context.getRound() == null ? null : context.getRound().getRoundCode());
        errorEvent.setStatus("FAILED");
        errorEvent.setMessage(ex.getMessage());
        try {
            context.getEmitter().send(SseEmitter.event().name("error").data(errorEvent, MediaType.APPLICATION_JSON));
        } catch (IOException ioException) {
            log.warn("failed to send workflow error event", ioException);
        }
    }

    private void prepareConversationContext(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        if (command == null) {
            throw BizException.illegalParam(ParamBizCodeConstant.REQUIRED_DTO);
        }
        if (!org.springframework.util.StringUtils.hasText(command.getMessage())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_MESSAGE);
        }

        String sessionCode = command.getSessionCode();
        Long userId = resolveUserId(command.getUserId());

        AiChatSessionDTO session;
        List<AiChatMessageDTO> sessionMessages;
        List<AiChatArtifactDTO> sessionArtifacts;
        if (!org.springframework.util.StringUtils.hasText(sessionCode)) {
            session = createSession(command, userId);
            sessionMessages = List.of();
            sessionArtifacts = List.of();
            command.setSessionCode(session.getSessionCode());
        } else {
            session = loadSession(sessionCode, userId);
            if (session == null) {
                log.warn("session not found, sessionCode={}, userId={}", sessionCode, userId);
                throw BizException.of(AiChatBizCodeConstant.CONVERSATION_NOT_FOUND, sessionCode);
            }
            sessionMessages = loadSessionMessages(sessionCode, userId);
            sessionArtifacts = loadSessionArtifacts(sessionCode, userId);
        }

        context.setSession(session);
        context.setSessionArtifacts(sessionArtifacts);
        context.getOrCreateUserMessageContext().setSessionMessages(sessionMessages);

        AiChatRoundDTO round = createRound(session, command, userId);
        context.setRound(round);

        AiChatMessageDTO lastMessage = sessionMessages.isEmpty() ? null : sessionMessages.get(sessionMessages.size() - 1);
        AiChatMessageDTO userMessage = historyRecorder.saveMessage(
                context,
                round.getRoundCode(),
                "USER",
                AiChatActorType.HUMAN.name(),
                resolveUserMessageType(round.getRoundType()),
                command.getMessage(),
                AiChatContentFormat.PLAIN_TEXT.name(),
                AiChatDisplayLevel.VISIBLE.name(),
                STATUS_SUCCESS,
                lastMessage == null ? null : lastMessage.getMessageCode(),
                lastMessage == null ? null : lastMessage.getMessageCode(),
                null
        );
        context.getOrCreateUserMessageContext().setCurrentMessage(userMessage);
        context.refreshUserMessageContext();
        context.getOrCreateNodeResult(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode()).setStatus(STATUS_SUCCESS);
        context.putNodeOutput(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode(), "session", session);
        context.putNodeOutput(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode(), "currentMessage", userMessage);
    }

    private void sendInitEvent(WorkflowContext context) {
        if (context.getEmitter() == null) {
            return;
        }
        AiChatQueryStreamEvent initEvent = new AiChatQueryStreamEvent();
        initEvent.setEventType("init");
        initEvent.setSessionCode(context.getSession() == null ? null : context.getSession().getSessionCode());
        initEvent.setSessionName(context.getSession() == null ? null : context.getSession().getSessionName());
        initEvent.setRoundCode(context.getRound() == null ? null : context.getRound().getRoundCode());
        initEvent.setStatus(STATUS_RUNNING);
        try {
            context.getEmitter().send(SseEmitter.event().name("init").data(initEvent, MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            throw new IllegalStateException("failed to send init event", ex);
        }
    }

    private AiChatSessionDTO createSession(AiChatQueryCommand command, Long userId) {
        AiChatSessionDTO session = new AiChatSessionDTO();
        session.setSessionCode(generateCode("session"));
        session.setUserId(userId);
        session.setBusinessType(resolveBusinessType(command.getBusinessType()));
        session.setSessionName(resolveSessionName(command));
        session.setPinned(Boolean.FALSE);
        return sessionService.add(session);
    }

    private AiChatSessionDTO loadSession(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return sessionService.get(query);
    }

    private List<AiChatMessageDTO> loadSessionMessages(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return messageService.queryAll(query).stream()
                .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<AiChatArtifactDTO> loadSessionArtifacts(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return artifactService.queryAll(query);
    }

    private AiChatRoundDTO createRound(AiChatSessionDTO session,
                                       AiChatQueryCommand command,
                                       Long userId) {
        AiChatRoundDTO round = new AiChatRoundDTO();
        round.setRoundCode(generateCode("round"));
        round.setRoundType(resolveRoundType(command));
        round.setParentRoundCode(resolveParentRoundCode(session.getSessionCode(), userId));
        round.setSessionCode(session.getSessionCode());
        round.setUserId(userId);
        round.setModelCode(resolveModelCode(command.getApiModel()));
        round.setActualModel(resolveActualModel(command.getApiModel()));
        round.setStatus(STATUS_RUNNING);
        return roundService.add(round);
    }

    private Long resolveUserId(Long userId) {
        if (SecurityContextHolder.get() != null && SecurityContextHolder.get().subject() != null) {
            return SecurityContextHolder.get().subject().userId();
        }
        return userId == null ? 0L : userId;
    }

    private AiChatBusinessType resolveBusinessType(AiChatBusinessType businessType) {
        return businessType == null ? AiChatBusinessType.CUSTOM : businessType;
    }

    private String resolveSessionName(AiChatQueryCommand command) {
        if (org.springframework.util.StringUtils.hasText(command.getSessionName())) {
            return command.getSessionName().trim();
        }
        if (!org.springframework.util.StringUtils.hasText(command.getMessage())) {
            return "新会话";
        }
        String content = command.getMessage().trim();
        return content.length() > 20 ? content.substring(0, 20) : content;
    }

    private AiChatRoundType resolveRoundType(AiChatQueryCommand command) {
        String explicitRoundType = readExtText(command, "roundType");
        if (explicitRoundType != null) {
            return AiChatRoundType.fromIntentType(explicitRoundType);
        }
        return AiChatRoundType.QUERY_RENDER;
    }

    private boolean hasExplicitRoundType(AiChatQueryCommand command) {
        return readExtText(command, "roundType") != null || readExtText(command, "intentType") != null;
    }

    private String readExtText(AiChatQueryCommand command, String key) {
        Object value = command == null || command.getExt() == null ? null : command.getExt().get(key);
        if (value instanceof String str && org.springframework.util.StringUtils.hasText(str)) {
            return str.trim();
        }
        return null;
    }

    private String resolveUserMessageType(AiChatRoundType roundType) {
        return AiChatMessageType.USER_INPUT.name();
    }

    private String resolveParentRoundCode(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        List<AiChatRoundDTO> rounds = roundService.queryAll(query);
        if (CollectionUtils.isEmpty(rounds)) {
            return null;
        }
        return rounds.get(rounds.size() - 1).getRoundCode();
    }

    private String resolveModelCode(String apiModel) {
        return org.springframework.util.StringUtils.hasText(apiModel) ? apiModel.trim() : "DEFAULT";
    }

    private String resolveActualModel(String apiModel) {
        return org.springframework.util.StringUtils.hasText(apiModel) ? apiModel.trim() : "DEFAULT";
    }

    private String generateCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
