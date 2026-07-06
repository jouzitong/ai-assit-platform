package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.conversation.constant.ConversationEventPhases;
import ai.platform.aiassit.conversation.constant.ConversationEventSources;
import ai.platform.aiassit.conversation.constant.ConversationEventTypes;
import ai.platform.aiassit.conversation.workflow.dto.chat.AiChatQueryCommand;
import ai.platform.aiassit.conversation.dto.chat.AiChatQueryResponse;
import ai.platform.aiassit.conversation.dto.chat.AiChatStreamReconnectRequest;
import ai.platform.aiassit.conversation.service.AiChatQueryService;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.conversation.workflow.context.WorkflowContext;
import ai.platform.aiassit.conversation.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.conversation.workflow.dto.AiChatQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.engine.IWorkflowEngine;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowAiChatQueryServiceImpl implements AiChatQueryService {

    private final IWorkflowEngine workflowEngine;
    private final AiChatSessionService sessionService;
    private final AiChatRoundService roundService;
    private final AiChatMessageService messageService;

    public WorkflowAiChatQueryServiceImpl(IWorkflowEngine workflowEngine,
                                          AiChatSessionService sessionService,
                                          AiChatRoundService roundService,
                                          AiChatMessageService messageService) {
        this.workflowEngine = workflowEngine;
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.messageService = messageService;
    }

    @Override
    public AiChatQueryResponse query(AiChatQueryCommand command) {
        WorkflowDefinition workflowDefinition = buildWorkflowDefinition();
        WorkflowContext workflowContext = buildWorkflowContext(command, workflowDefinition);
        workflowEngine.run(workflowContext);
        return buildQueryResponse(workflowContext);
    }

    @Override
    public SseEmitter queryStream(AiChatQueryCommand command) {
        SseEmitter emitter = new SseEmitter(0L);
        handleQueryStream(command, emitter);
        return emitter;
    }

    private void handleQueryStream(AiChatQueryCommand command, SseEmitter emitter) {
        WorkflowDefinition workflowDefinition = buildWorkflowDefinition();
        WorkflowContext workflowContext = buildWorkflowContext(command, workflowDefinition);
        workflowContext.setEmitter(emitter);
        workflowEngine.run(workflowContext);
    }

    @Override
    public SseEmitter reconnectStream(AiChatStreamReconnectRequest request, Long userId, String traceId) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            AiChatSessionDTO session = loadSession(request == null ? null : request.getSessionCode(), userId);
            if (session == null) {
                throw new IllegalArgumentException("conversation not found");
            }
            AiChatRoundDTO round = loadRound(request == null ? null : request.getRoundCode(), session.getSessionCode(), userId);
            if (round == null) {
                throw new IllegalArgumentException("round not found");
            }
            String answer = loadLatestAssistantAnswer(round.getRoundCode(), session.getSessionCode(), userId);

            sendInitEvent(emitter, traceId, session, round);
            if (StringUtils.hasText(answer)) {
                sendAnswerSnapshot(emitter, traceId, session, round, answer);
            }

            if ("SUCCESS".equalsIgnoreCase(round.getStatus())) {
                sendCompleteEvent(emitter, traceId, session, round, answer);
            } else if ("FAILED".equalsIgnoreCase(round.getStatus())) {
                sendErrorEvent(emitter, traceId, session, round, "workflow execution failed");
            }
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    private WorkflowContext buildWorkflowContext(AiChatQueryCommand command, WorkflowDefinition workflowDefinition) {
        WorkflowContext context = new WorkflowContext();
        context.setCommand(command);
        context.setWorkflowDefinition(workflowDefinition);
        context.setWorkflowCode(workflowDefinition == null ? "ai-chat-query-workflow" : workflowDefinition.getWorkflowCode());
        return context;
    }

    private WorkflowDefinition buildWorkflowDefinition() {
        Map<String, WorkflowNodeConfig> nodes = new LinkedHashMap<>();
        nodes.put(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(), new WorkflowNodeConfig(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(), WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), java.util.List.of()));
        nodes.put(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), new WorkflowNodeConfig(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), WorkflowNodeCodes.RENDER.getNodeCode(), java.util.List.of()));
        nodes.put(WorkflowNodeCodes.RENDER.getNodeCode(), new WorkflowNodeConfig(WorkflowNodeCodes.RENDER.getNodeCode(), null, java.util.List.of()));
        return new WorkflowDefinition("ai-chat-query-workflow", nodes, WorkflowNodeCodes.QUERY_PLANNING.getNodeCode());
    }

    private AiChatQueryResponse buildQueryResponse(WorkflowContext context) {
        AiChatQueryResponse response = new AiChatQueryResponse();
        response.setRequestId(context.getCommand() == null ? null : context.getCommand().getTraceId());
        response.setSessionCode(context.getSession() == null ? null : context.getSession().getSessionCode());
        response.setRoundCode(context.getRound() == null ? null : context.getRound().getRoundCode());
        response.setModelCode(context.getRound() == null ? null : context.getRound().getModelCode());
        response.setProviderCode(null);
        response.setAnswer(context.getRenderedAnswer());
        response.setStatus(context.getRound() == null ? null : context.getRound().getStatus());
        String error = context.get(WorkflowContextKeys.Common.ERROR);
        response.setFinishReason(StringUtils.hasText(error) ? error : response.getStatus());
        return response;
    }

    private AiChatSessionDTO loadSession(String sessionCode, Long userId) {
        if (!StringUtils.hasText(sessionCode)) {
            throw new IllegalArgumentException("sessionCode is required");
        }
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return sessionService.get(query);
    }

    private AiChatRoundDTO loadRound(String roundCode, String sessionCode, Long userId) {
        if (!StringUtils.hasText(roundCode)) {
            throw new IllegalArgumentException("roundCode is required");
        }
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setRoundCode(roundCode);
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return roundService.queryAll(query).stream()
                .max(Comparator.comparing(AiChatRoundDTO::getId, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
    }

    private String loadLatestAssistantAnswer(String roundCode, String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setRoundCode(roundCode);
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        List<AiChatMessageDTO> messages = messageService.queryAll(query).stream()
                .filter(message -> message != null && StringUtils.hasText(message.getContent()))
                .filter(message -> "ASSISTANT".equalsIgnoreCase(message.getRole()))
                .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        return messages.isEmpty() ? null : messages.get(messages.size() - 1).getContent();
    }

    private void sendInitEvent(SseEmitter emitter,
                               String traceId,
                               AiChatSessionDTO session,
                               AiChatRoundDTO round) throws IOException {
        AiChatQueryStreamEvent initEvent = new AiChatQueryStreamEvent();
        initEvent.setEventType(ConversationEventTypes.PROGRESS);
        initEvent.setSource(ConversationEventSources.CONVERSATION);
        initEvent.setPhase(ConversationEventPhases.STARTED);
        initEvent.setRequestId(traceId);
        initEvent.setSessionCode(session.getSessionCode());
        initEvent.setSessionName(session.getSessionName());
        initEvent.setRoundCode(round.getRoundCode());
        initEvent.setStatus(round.getStatus());
        initEvent.setMessage("conversation started");
        emitter.send(SseEmitter.event().name(ConversationEventTypes.PROGRESS).data(initEvent, MediaType.APPLICATION_JSON));
    }

    private void sendAnswerSnapshot(SseEmitter emitter,
                                    String traceId,
                                    AiChatSessionDTO session,
                                    AiChatRoundDTO round,
                                    String answer) throws IOException {
        AiChatQueryStreamEvent answerEvent = new AiChatQueryStreamEvent();
        answerEvent.setEventType(ConversationEventTypes.ANSWER);
        answerEvent.setSource(ConversationEventSources.CONVERSATION);
        answerEvent.setPhase(ConversationEventPhases.READY);
        answerEvent.setRequestId(traceId);
        answerEvent.setSessionCode(session.getSessionCode());
        answerEvent.setSessionName(session.getSessionName());
        answerEvent.setRoundCode(round.getRoundCode());
        answerEvent.setAnswer(answer);
        answerEvent.setStatus(round.getStatus());
        answerEvent.setMessage("replayed current answer snapshot");
        emitter.send(SseEmitter.event().name(ConversationEventTypes.ANSWER).data(answerEvent, MediaType.APPLICATION_JSON));
    }

    private void sendCompleteEvent(SseEmitter emitter,
                                   String traceId,
                                   AiChatSessionDTO session,
                                   AiChatRoundDTO round,
                                   String answer) throws IOException {
        AiChatQueryStreamEvent completeEvent = new AiChatQueryStreamEvent();
        completeEvent.setEventType(ConversationEventTypes.COMPLETE);
        completeEvent.setSource(ConversationEventSources.CONVERSATION);
        completeEvent.setPhase(ConversationEventPhases.COMPLETED);
        completeEvent.setRequestId(traceId);
        completeEvent.setSessionCode(session.getSessionCode());
        completeEvent.setSessionName(session.getSessionName());
        completeEvent.setRoundCode(round.getRoundCode());
        completeEvent.setAnswer(answer);
        completeEvent.setStatus("SUCCESS");
        emitter.send(SseEmitter.event().name(ConversationEventTypes.COMPLETE).data(completeEvent, MediaType.APPLICATION_JSON));
    }

    private void sendErrorEvent(SseEmitter emitter,
                                String traceId,
                                AiChatSessionDTO session,
                                AiChatRoundDTO round,
                                String message) throws IOException {
        AiChatQueryStreamEvent errorEvent = new AiChatQueryStreamEvent();
        errorEvent.setEventType(ConversationEventTypes.ERROR);
        errorEvent.setSource(ConversationEventSources.CONVERSATION);
        errorEvent.setPhase(ConversationEventPhases.FAILED);
        errorEvent.setRequestId(traceId);
        errorEvent.setSessionCode(session.getSessionCode());
        errorEvent.setSessionName(session.getSessionName());
        errorEvent.setRoundCode(round.getRoundCode());
        errorEvent.setStatus("FAILED");
        errorEvent.setMessage(message);
        emitter.send(SseEmitter.event().name(ConversationEventTypes.ERROR).data(errorEvent, MediaType.APPLICATION_JSON));
    }
}
