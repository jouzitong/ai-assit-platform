package ai.platform.aiassit.chat.core.workflow.engine.impl;

import ai.platform.aiassit.chat.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryStreamEvent;
import ai.platform.aiassist.service.ai.api.dto.IntentAnalyzeResponse;
import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.chat.core.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.chat.core.workflow.engine.IWorkflowEngine;
import ai.platform.aiassit.chat.core.workflow.node.IWorkflowNode;
import ai.platform.aiassit.chat.core.workflow.planning.service.WorkflowIntentAnalyzeService;
import ai.platform.aiassit.chat.core.workflow.support.WorkflowHistoryRecorder;
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

    private final Map<String, IWorkflowNode> nodeRegistry;
    private final AiChatSessionService sessionService;
    private final AiChatMessageService messageService;
    private final AiChatArtifactService artifactService;
    private final AiChatRoundService roundService;
    private final WorkflowHistoryRecorder historyRecorder;
    private final AsyncTaskManager asyncTaskManager;
    private final WorkflowIntentAnalyzeService workflowIntentAnalyzeService;

    public DefaultWorkflowEngineImpl(List<IWorkflowNode> nodes,
                                     AiChatSessionService sessionService,
                                     AiChatMessageService messageService,
                                     AiChatArtifactService artifactService,
                                     AiChatRoundService roundService,
                                     WorkflowHistoryRecorder historyRecorder,
                                     AsyncTaskManager asyncTaskManager,
                                     WorkflowIntentAnalyzeService workflowIntentAnalyzeService) {
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
        prepareConversationContext(context);
        sendInitEvent(context);
        prepareBaseIntentAnalysis(context);

        WorkflowDefinition definition = context.getWorkflowDefinition();
        if (definition == null) {
            context.put(WorkflowContextKeys.Common.ERROR, "workflow definition is required");
            context.publishEvent("workflow-error", "workflow definition is required");
            return;
        }
        String currentNodeId = definition.getStartNodeId();
        while (currentNodeId != null) {
            WorkflowNodeConfig workflowNodeConfig = definition.getNodes().get(currentNodeId);
            if (workflowNodeConfig == null) {
                context.put(WorkflowContextKeys.Common.ERROR, "workflow node config not found: " + currentNodeId);
                context.publishEvent("workflow-error", "workflow node config not found: " + currentNodeId);
                return;
            }
            IWorkflowNode currentNode = nodeRegistry.get(workflowNodeConfig.getNodeId());
            if (currentNode == null) {
                context.put(WorkflowContextKeys.Common.ERROR, "workflow node not found: " + workflowNodeConfig.getNodeId());
                context.publishEvent("workflow-error", "workflow node not found: " + workflowNodeConfig.getNodeId());
                return;
            }
            context.publishEvent("node-start",
                    "start node: " + workflowNodeConfig.getNodeId());
            NodeResult result;
            try {
                result = currentNode.execute(context, workflowNodeConfig);
            } catch (Exception e) {
                log.error("Error executing node: {}. ", workflowNodeConfig.getNodeId(), e);
                context.put(WorkflowContextKeys.Common.ERROR, "Error executing node: " + workflowNodeConfig.getNodeId() + ", error=" + e.getMessage());
                context.publishEvent("node-failed", "node failed: " + workflowNodeConfig.getNodeId() + ", error=" + e.getMessage());
                return;
            }
            if (!result.isSuccess()) {
                context.put(WorkflowContextKeys.Common.ERROR, result.getErrorMessage());
                context.publishEvent("node-failed",
                        "node failed: " + workflowNodeConfig.getNodeId() + ", error=" + result.getErrorMessage());
                return;
            }
            context.publishEvent("node-complete",
                    "complete node: " + workflowNodeConfig.getNodeId());
            if (StringUtils.isNotBlank(result.getNextNodeId())) {
                currentNodeId = result.getNextNodeId();
            } else {
                currentNodeId = workflowNodeConfig.getNextNodeId();
            }
        }
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
        AiChatRoundDTO round = context == null ? null : context.getRound();
        if (round == null || round.getId() == null) {
            return;
        }
        AiChatRoundType roundType = AiChatRoundType.fromIntentType(response == null ? null : response.getIntentType());
        if (roundType.name().equals(round.getRoundType())) {
            return;
        }
        AiChatRoundDTO update = new AiChatRoundDTO();
        update.setRoundType(roundType.name());
        roundService.edit(round.getId(), update);
        round.setRoundType(roundType.name());
    }

    private WorkflowDefinition buildSimpleChatWorkflowDefinition() {
        Map<String, WorkflowNodeConfig> nodes = new LinkedHashMap<>();
        nodes.put(WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode(),
                new WorkflowNodeConfig(WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode(), null, List.of()));
        return new WorkflowDefinition("ai-chat-simple-chat-workflow", nodes, WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode());
    }

    private WorkflowDefinition buildQueryRenderWorkflowDefinition() {
        Map<String, WorkflowNodeConfig> nodes = new LinkedHashMap<>();
        nodes.put(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(),
                new WorkflowNodeConfig(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(), WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), List.of()));
        nodes.put(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(),
                new WorkflowNodeConfig(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), WorkflowNodeCodes.RENDER.getNodeCode(), List.of()));
        nodes.put(WorkflowNodeCodes.RENDER.getNodeCode(),
                new WorkflowNodeConfig(WorkflowNodeCodes.RENDER.getNodeCode(), null, List.of()));
        return new WorkflowDefinition("ai-chat-query-render-workflow", nodes, WorkflowNodeCodes.QUERY_PLANNING.getNodeCode());
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

        AiChatRoundDTO round = createRound(session, sessionMessages, command, userId);
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
                                       List<AiChatMessageDTO> sessionMessages,
                                       AiChatQueryCommand command,
                                       Long userId) {
        AiChatRoundDTO round = new AiChatRoundDTO();
        round.setRoundCode(generateCode("round"));
        round.setRoundType(resolveRoundType(command).name());
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
        Object extValue = command == null || command.getExt() == null ? null : command.getExt().get("roundType");
        if (extValue instanceof String str && org.springframework.util.StringUtils.hasText(str)) {
            return AiChatRoundType.fromIntentType(str);
        }
        Object intentTypeValue = command == null || command.getExt() == null ? null : command.getExt().get("intentType");
        if (intentTypeValue instanceof String str && org.springframework.util.StringUtils.hasText(str)) {
            return AiChatRoundType.fromIntentType(str);
        }
        return AiChatRoundType.QUERY_RENDER;
    }

    private String resolveUserMessageType(String roundType) {
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
