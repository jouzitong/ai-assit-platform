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
import ai.platform.aiassit.conversation.workflow.runtime.ConversationCancellation;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationCancelledException;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationEventPublisher;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DefaultConversationExecutionServiceImpl implements ConversationExecutionService {

    private final IWorkflowEngine workflowEngine;
    private final ConversationPreparationService preparationService;
    private final AiChatSessionService sessionService;
    private final AiChatRoundService roundService;
    private final AiChatMessageService messageService;

    public DefaultConversationExecutionServiceImpl(IWorkflowEngine workflowEngine,
                                                   ConversationPreparationService preparationService,
                                                   AiChatSessionService sessionService,
                                                   AiChatRoundService roundService,
                                                   AiChatMessageService messageService) {
        this.workflowEngine = workflowEngine;
        this.preparationService = preparationService;
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
    public ConversationQueryResponse executeStream(ConversationQueryCommand command,
                                                   ConversationEventPublisher eventPublisher,
                                                   ConversationCancellation cancellation) {
        long startedAt = System.currentTimeMillis();
        ConversationRuntimeContext context = buildConversationRuntimeContext(command);
        context.setEventPublisher(eventPublisher == null ? ConversationEventPublisher.NOOP : eventPublisher);
        context.setCancellation(cancellation == null ? ConversationCancellation.NONE : cancellation);
        try {
            log.info("开始执行对话流业务链路，context={}", context);
            context.checkCancellation();
            preparationService.prepare(context);
            log.info("对话流上下文准备完成，context={}", context);
            publishInitEvent(context);
            context.checkCancellation();
            workflowEngine.run(context);
            context.checkCancellation();
            String error = context.get(ConversationRuntimeContextKeys.Common.ERROR);
            if (StringUtils.hasText(error)) {
                throw BizException.of(AiChatBizCodeConstant.WORKFLOW_EXECUTION_FAILED, error);
            }
            context.publishCompleteEvent(
                    ConversationEventSources.CONVERSATION,
                    ConversationEventPhases.COMPLETED,
                    "conversation completed",
                    context.getRenderedAnswer(),
                    "SUCCESS"
            );
            log.info("对话流业务链路执行成功，context={}, durationMs={}", context, System.currentTimeMillis() - startedAt);
            return buildQueryResponse(context);
        } catch (ConversationCancelledException ex) {
            markRoundCancelled(context);
            log.info("对话流业务链路已取消，context={}, durationMs={}", context, System.currentTimeMillis() - startedAt);
            throw ex;
        } catch (Exception ex) {
            if (!StringUtils.hasText(context.get(ConversationRuntimeContextKeys.Common.ERROR))) {
                context.publishErrorEvent(
                        ConversationEventSources.CONVERSATION,
                        ConversationEventPhases.FAILED,
                        ex.getMessage()
                );
            }
            log.warn("对话流业务链路执行异常，context={}, durationMs={}, error={}",
                    context, System.currentTimeMillis() - startedAt, ex.getMessage());
            throw ex;
        }
    }

    @Override
    public List<ConversationQueryStreamEvent> replayStream(ConversationStreamReconnectRequest request,
                                                           Long userId,
                                                           String traceId) {
        AiChatSessionDTO session = loadSession(request == null ? null : request.getSessionCode(), userId);
        if (session == null) {
            throw BizException.of(AiChatBizCodeConstant.CONVERSATION_NOT_FOUND);
        }
        AiChatRoundDTO round = loadRound(request == null ? null : request.getRoundCode(), session.getSessionCode(), userId);
        if (round == null) {
            throw BizException.of(AiChatBizCodeConstant.CONVERSATION_ROUND_NOT_FOUND);
        }
        String answer = loadLatestAssistantAnswer(round.getRoundCode(), session.getSessionCode(), userId);
        List<ConversationQueryStreamEvent> events = new ArrayList<>();
        events.add(buildInitEvent(traceId, session, round));
        if (StringUtils.hasText(answer)) {
            events.add(buildAnswerSnapshot(traceId, session, round, answer));
        }
        if ("SUCCESS".equalsIgnoreCase(round.getStatus())) {
            events.add(buildCompleteEvent(traceId, session, round, answer));
        } else if ("FAILED".equalsIgnoreCase(round.getStatus())) {
            events.add(buildErrorEvent(traceId, session, round, "workflow execution failed"));
        } else if ("CANCELLED".equalsIgnoreCase(round.getStatus())
                || "CANCELED".equalsIgnoreCase(round.getStatus())) {
            events.add(buildCancelledEvent(traceId, session, round));
        }
        for (int i = 0; i < events.size(); i++) {
            events.get(i).setEventId(String.valueOf(i + 1L));
            events.get(i).setTimestamp(System.currentTimeMillis());
        }
        return events;
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
                "conversation started",
                buildInitializationPayload(context)
        );
    }

    private Map<String, Object> buildInitializationPayload(ConversationRuntimeContext context) {
        Map<String, Object> ext = new LinkedHashMap<>();
        Map<String, Object> conversation = new LinkedHashMap<>();
        if (context.getSession() != null) {
            conversation.put("id", context.getSession().getSessionCode());
            conversation.put("sessionCode", context.getSession().getSessionCode());
            conversation.put("title", context.getSession().getSessionName());
        }
        if (context.getRound() != null) {
            conversation.put("model", Map.of(
                    "id", StringUtils.hasText(context.getRound().getModelCode())
                            ? context.getRound().getModelCode()
                            : "default"
            ));
        }
        ext.put("conversation", conversation);

        Map<String, Object> round = new LinkedHashMap<>();
        if (context.getRound() != null) {
            round.put("id", context.getRound().getRoundCode());
            round.put("roundCode", context.getRound().getRoundCode());
            round.put("status", context.getRound().getStatus());
        }
        ext.put("round", round);

        AiChatMessageDTO currentMessage = context.getOrCreateUserMessageContext().getCurrentMessage();
        if (currentMessage != null) {
            Map<String, Object> userMessage = new LinkedHashMap<>();
            userMessage.put("id", currentMessage.getMessageCode());
            userMessage.put("role", "user");
            userMessage.put("content", List.of(Map.of(
                    "type", "text",
                    "text", currentMessage.getContent() == null ? "" : currentMessage.getContent()
            )));
            ext.put("userMessage", userMessage);
        }
        return ext;
    }

    private ConversationQueryResponse buildQueryResponse(ConversationRuntimeContext context) {
        ConversationQueryResponse response = new ConversationQueryResponse();
        response.setRequestId(context.getCommand() == null ? null : context.getCommand().getTraceId());
        response.setSessionCode(context.getSession() == null ? null : context.getSession().getSessionCode());
        response.setRoundCode(context.getRound() == null ? null : context.getRound().getRoundCode());
        response.setModelCode(context.getRound() == null ? null : context.getRound().getModelCode());
        response.setClientType(null);
        response.setAnswer(context.getRenderedAnswer());
        response.setStatus(context.getRound() == null ? null : context.getRound().getStatus());
        String error = context.get(ConversationRuntimeContextKeys.Common.ERROR);
        response.setFinishReason(StringUtils.hasText(error) ? error : response.getStatus());
        return response;
    }

    private void markRoundCancelled(ConversationRuntimeContext context) {
        AiChatRoundDTO round = context == null ? null : context.getRound();
        if (round == null || round.getId() == null) {
            return;
        }
        AiChatRoundDTO update = new AiChatRoundDTO();
        update.setStatus("CANCELLED");
        roundService.edit(round.getId(), update);
        round.setStatus("CANCELLED");
    }

    private AiChatSessionDTO loadSession(String sessionCode, Long userId) {
        if (!StringUtils.hasText(sessionCode)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_SESSION_CODE);
        }
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return sessionService.get(query);
    }

    private AiChatRoundDTO loadRound(String roundCode, String sessionCode, Long userId) {
        if (!StringUtils.hasText(roundCode)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_ROUND_CODE);
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

    private ConversationQueryStreamEvent buildInitEvent(String traceId,
                                                        AiChatSessionDTO session,
                                                        AiChatRoundDTO round) {
        ConversationQueryStreamEvent initEvent = new ConversationQueryStreamEvent();
        initEvent.setEventType("progress");
        initEvent.setSource(ConversationEventSources.CONVERSATION);
        initEvent.setPhase(ConversationEventPhases.STARTED);
        initEvent.setRequestId(traceId);
        initEvent.setSessionCode(session.getSessionCode());
        initEvent.setSessionName(session.getSessionName());
        initEvent.setRoundCode(round.getRoundCode());
        initEvent.setStatus(round.getStatus());
        initEvent.setMessage("conversation started");
        return initEvent;
    }

    private ConversationQueryStreamEvent buildAnswerSnapshot(String traceId,
                                                             AiChatSessionDTO session,
                                                             AiChatRoundDTO round,
                                                             String answer) {
        ConversationQueryStreamEvent answerEvent = new ConversationQueryStreamEvent();
        answerEvent.setEventType("answer");
        answerEvent.setSource(ConversationEventSources.CONVERSATION);
        answerEvent.setPhase("READY");
        answerEvent.setRequestId(traceId);
        answerEvent.setSessionCode(session.getSessionCode());
        answerEvent.setSessionName(session.getSessionName());
        answerEvent.setRoundCode(round.getRoundCode());
        answerEvent.setAnswer(answer);
        answerEvent.setStatus(round.getStatus());
        answerEvent.setMessage("replayed current answer snapshot");
        return answerEvent;
    }

    private ConversationQueryStreamEvent buildCompleteEvent(String traceId,
                                                            AiChatSessionDTO session,
                                                            AiChatRoundDTO round,
                                                            String answer) {
        ConversationQueryStreamEvent completeEvent = new ConversationQueryStreamEvent();
        completeEvent.setEventType("complete");
        completeEvent.setSource(ConversationEventSources.CONVERSATION);
        completeEvent.setPhase(ConversationEventPhases.COMPLETED);
        completeEvent.setRequestId(traceId);
        completeEvent.setSessionCode(session.getSessionCode());
        completeEvent.setSessionName(session.getSessionName());
        completeEvent.setRoundCode(round.getRoundCode());
        completeEvent.setAnswer(answer);
        completeEvent.setStatus("SUCCESS");
        return completeEvent;
    }

    private ConversationQueryStreamEvent buildErrorEvent(String traceId,
                                                         AiChatSessionDTO session,
                                                         AiChatRoundDTO round,
                                                         String message) {
        ConversationQueryStreamEvent errorEvent = new ConversationQueryStreamEvent();
        errorEvent.setEventType("error");
        errorEvent.setSource(ConversationEventSources.CONVERSATION);
        errorEvent.setPhase(ConversationEventPhases.FAILED);
        errorEvent.setRequestId(traceId);
        errorEvent.setSessionCode(session.getSessionCode());
        errorEvent.setSessionName(session.getSessionName());
        errorEvent.setRoundCode(round.getRoundCode());
        errorEvent.setStatus("FAILED");
        errorEvent.setMessage(message);
        return errorEvent;
    }

    private ConversationQueryStreamEvent buildCancelledEvent(String traceId,
                                                             AiChatSessionDTO session,
                                                             AiChatRoundDTO round) {
        ConversationQueryStreamEvent event = new ConversationQueryStreamEvent();
        event.setEventType("run.cancelled");
        event.setSource(ConversationEventSources.CONVERSATION);
        event.setPhase("CANCELLED");
        event.setRequestId(traceId);
        event.setSessionCode(session.getSessionCode());
        event.setSessionName(session.getSessionName());
        event.setRoundCode(round.getRoundCode());
        event.setStatus("CANCELLED");
        event.setMessage("conversation run cancelled");
        return event;
    }
}
