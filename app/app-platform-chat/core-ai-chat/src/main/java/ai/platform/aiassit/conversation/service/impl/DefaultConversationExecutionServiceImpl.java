package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.conversation.constant.ConversationEventPhases;
import ai.platform.aiassit.conversation.constant.ConversationEventSources;
import ai.platform.aiassit.conversation.dto.chat.ConversationQueryResponse;
import ai.platform.aiassit.conversation.dto.chat.ConversationStreamReconnectRequest;
import ai.platform.aiassit.conversation.service.ConversationExecutionService;
import ai.platform.aiassit.conversation.workflow.constants.ConversationRuntimeContextKeys;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.workflow.engine.IWorkflowEngine;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import org.arthena.framework.common.thread.AsyncTaskManager;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@Service
public class DefaultConversationExecutionServiceImpl implements ConversationExecutionService {

    private final IWorkflowEngine workflowEngine;
    private final ConversationPreparationService preparationService;
    private final AsyncTaskManager asyncTaskManager;
    private final AiChatSessionService sessionService;
    private final AiChatRoundService roundService;
    private final AiChatMessageService messageService;

    public DefaultConversationExecutionServiceImpl(IWorkflowEngine workflowEngine,
                                                   ConversationPreparationService preparationService,
                                                   AsyncTaskManager asyncTaskManager,
                                                   AiChatSessionService sessionService,
                                                   AiChatRoundService roundService,
                                                   AiChatMessageService messageService) {
        this.workflowEngine = workflowEngine;
        this.preparationService = preparationService;
        this.asyncTaskManager = asyncTaskManager;
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.messageService = messageService;
    }

    @Override
    public ConversationQueryResponse execute(ConversationQueryCommand command) {
        ConversationRuntimeContext workflowContext = buildConversationRuntimeContext(command);
        preparationService.prepare(workflowContext);
        workflowEngine.run(workflowContext);
        return buildQueryResponse(workflowContext);
    }

    @Override
    public SseEmitter executeStream(ConversationQueryCommand command) {
        SseEmitter emitter = new SseEmitter(0L);
        ConversationRuntimeContext workflowContext = buildConversationRuntimeContext(command);
        workflowContext.setEmitter(emitter);
        asyncTaskManager.submit(() -> runStream(workflowContext));
        return emitter;
    }

    private void runStream(ConversationRuntimeContext context) {
        SseEmitter emitter = context.getEmitter();
        try {
            preparationService.prepare(context);
            publishInitEvent(context);
            workflowEngine.run(context);
            String error = context.get(ConversationRuntimeContextKeys.Common.ERROR);
            if (StringUtils.hasText(error)) {
                emitter.completeWithError(new IllegalStateException(error));
                return;
            }
            context.publishCompleteEvent(
                    ConversationEventSources.CONVERSATION,
                    ConversationEventPhases.COMPLETED,
                    "conversation completed",
                    context.getRenderedAnswer(),
                    "SUCCESS"
            );
            emitter.complete();
        } catch (Exception ex) {
            if (!StringUtils.hasText(context.get(ConversationRuntimeContextKeys.Common.ERROR))) {
                context.publishErrorEvent(
                        ConversationEventSources.CONVERSATION,
                        ConversationEventPhases.FAILED,
                        ex.getMessage()
                );
            }
            emitter.completeWithError(ex);
        }
    }

    @Override
    public SseEmitter reconnectStream(ConversationStreamReconnectRequest request, Long userId, String traceId) {
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

    private ConversationRuntimeContext buildConversationRuntimeContext(ConversationQueryCommand command) {
        ConversationRuntimeContext context = new ConversationRuntimeContext();
        context.setCommand(command);
        context.setWorkflowCode("ai-chat-intent-routing");
        return context;
    }

    private void publishInitEvent(ConversationRuntimeContext context) {
        context.publishProgressEvent(
                ConversationEventSources.CONVERSATION,
                ConversationEventPhases.STARTED,
                "conversation started"
        );
    }

    private ConversationQueryResponse buildQueryResponse(ConversationRuntimeContext context) {
        ConversationQueryResponse response = new ConversationQueryResponse();
        response.setRequestId(context.getCommand() == null ? null : context.getCommand().getTraceId());
        response.setSessionCode(context.getSession() == null ? null : context.getSession().getSessionCode());
        response.setRoundCode(context.getRound() == null ? null : context.getRound().getRoundCode());
        response.setModelCode(context.getRound() == null ? null : context.getRound().getModelCode());
        response.setProviderCode(null);
        response.setAnswer(context.getRenderedAnswer());
        response.setStatus(context.getRound() == null ? null : context.getRound().getStatus());
        String error = context.get(ConversationRuntimeContextKeys.Common.ERROR);
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
        ConversationQueryStreamEvent initEvent = new ConversationQueryStreamEvent();
        initEvent.setEventType("PROGRESS");
        initEvent.setSource(ConversationEventSources.CONVERSATION);
        initEvent.setPhase(ConversationEventPhases.STARTED);
        initEvent.setRequestId(traceId);
        initEvent.setSessionCode(session.getSessionCode());
        initEvent.setSessionName(session.getSessionName());
        initEvent.setRoundCode(round.getRoundCode());
        initEvent.setStatus(round.getStatus());
        initEvent.setMessage("conversation started");
        emitter.send(SseEmitter.event().name("PROGRESS").data(initEvent, MediaType.APPLICATION_JSON));
    }

    private void sendAnswerSnapshot(SseEmitter emitter,
                                    String traceId,
                                    AiChatSessionDTO session,
                                    AiChatRoundDTO round,
                                    String answer) throws IOException {
        ConversationQueryStreamEvent answerEvent = new ConversationQueryStreamEvent();
        answerEvent.setEventType("ANSWER");
        answerEvent.setSource(ConversationEventSources.CONVERSATION);
        answerEvent.setPhase("READY");
        answerEvent.setRequestId(traceId);
        answerEvent.setSessionCode(session.getSessionCode());
        answerEvent.setSessionName(session.getSessionName());
        answerEvent.setRoundCode(round.getRoundCode());
        answerEvent.setAnswer(answer);
        answerEvent.setStatus(round.getStatus());
        answerEvent.setMessage("replayed current answer snapshot");
        emitter.send(SseEmitter.event().name("ANSWER").data(answerEvent, MediaType.APPLICATION_JSON));
    }

    private void sendCompleteEvent(SseEmitter emitter,
                                   String traceId,
                                   AiChatSessionDTO session,
                                   AiChatRoundDTO round,
                                   String answer) throws IOException {
        ConversationQueryStreamEvent completeEvent = new ConversationQueryStreamEvent();
        completeEvent.setEventType("COMPLETE");
        completeEvent.setSource(ConversationEventSources.CONVERSATION);
        completeEvent.setPhase(ConversationEventPhases.COMPLETED);
        completeEvent.setRequestId(traceId);
        completeEvent.setSessionCode(session.getSessionCode());
        completeEvent.setSessionName(session.getSessionName());
        completeEvent.setRoundCode(round.getRoundCode());
        completeEvent.setAnswer(answer);
        completeEvent.setStatus("SUCCESS");
        emitter.send(SseEmitter.event().name("COMPLETE").data(completeEvent, MediaType.APPLICATION_JSON));
    }

    private void sendErrorEvent(SseEmitter emitter,
                                String traceId,
                                AiChatSessionDTO session,
                                AiChatRoundDTO round,
                                String message) throws IOException {
        ConversationQueryStreamEvent errorEvent = new ConversationQueryStreamEvent();
        errorEvent.setEventType("ERROR");
        errorEvent.setSource(ConversationEventSources.CONVERSATION);
        errorEvent.setPhase(ConversationEventPhases.FAILED);
        errorEvent.setRequestId(traceId);
        errorEvent.setSessionCode(session.getSessionCode());
        errorEvent.setSessionName(session.getSessionName());
        errorEvent.setRoundCode(round.getRoundCode());
        errorEvent.setStatus("FAILED");
        errorEvent.setMessage(message);
        emitter.send(SseEmitter.event().name("ERROR").data(errorEvent, MediaType.APPLICATION_JSON));
    }
}
