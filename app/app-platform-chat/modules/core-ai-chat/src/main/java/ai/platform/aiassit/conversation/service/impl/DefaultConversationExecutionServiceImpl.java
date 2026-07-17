package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.agent.runtime.AgentConversationOutcome;
import ai.platform.aiassit.agent.runtime.AgentConversationRequest;
import ai.platform.aiassit.agent.runtime.AgentConversationRunner;
import ai.platform.aiassit.agent.runtime.AgentTarget;
import ai.platform.aiassit.conversation.data.enums.ConversationActorType;
import ai.platform.aiassit.conversation.data.enums.ConversationContentFormat;
import ai.platform.aiassit.conversation.data.enums.ConversationDisplayLevel;
import ai.platform.aiassit.conversation.data.enums.ConversationMessageType;
import ai.platform.aiassit.conversation.constant.ConversationEventPhases;
import ai.platform.aiassit.conversation.constant.ConversationEventSources;
import ai.platform.aiassit.conversation.dto.chat.ConversationQueryResponse;
import ai.platform.aiassit.conversation.dto.chat.ConversationStreamReconnectRequest;
import ai.platform.aiassit.conversation.service.ConversationExecutionService;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationCancellation;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationCancelledException;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationEventPublisher;
import ai.platform.aiassit.conversation.workflow.support.AgentConversationHistoryRecorder;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.data.entity.req.ConversationHistoryQueryRequest;
import ai.platform.aiassit.conversation.data.service.ConversationMessageService;
import ai.platform.aiassit.conversation.data.service.ConversationRoundService;
import ai.platform.aiassit.conversation.data.service.ConversationSessionService;
import ai.platform.aiassit.knowledge.manage.service.AiKbStoreService;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.ChatMessage;
import ai.platform.aiassit.service.ai.api.enums.MessageRole;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunEvent;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class DefaultConversationExecutionServiceImpl implements ConversationExecutionService {

    private static final int MAX_HISTORY_MESSAGES = 40;
    private static final int MAX_HISTORY_CHARACTERS = 60_000;

    private final AgentConversationRunner agentConversationRunner;
    private final ConversationPreparationService preparationService;
    private final AgentConversationHistoryRecorder historyRecorder;
    private final ConversationSessionService sessionService;
    private final ConversationRoundService roundService;
    private final ConversationMessageService messageService;
    private final AiKbStoreService kbStoreService;

    public DefaultConversationExecutionServiceImpl(AgentConversationRunner agentConversationRunner,
                                                   ConversationPreparationService preparationService,
                                                   AgentConversationHistoryRecorder historyRecorder,
                                                   ConversationSessionService sessionService,
                                                   ConversationRoundService roundService,
                                                   ConversationMessageService messageService,
                                                   AiKbStoreService kbStoreService) {
        this.agentConversationRunner = agentConversationRunner;
        this.preparationService = preparationService;
        this.historyRecorder = historyRecorder;
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.messageService = messageService;
        this.kbStoreService = kbStoreService;
    }

    @Override
    public ConversationQueryResponse execute(ConversationQueryCommand command) {
        return executeStream(command, ConversationEventPublisher.NOOP, ConversationCancellation.NONE);
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
            AgentConversationRequest request = buildAgentRequest(context);
            StringBuilder streamedAnswer = new StringBuilder();
            AgentConversationOutcome outcome = agentConversationRunner.run(
                    request,
                    event -> handleAgentEvent(context, event, streamedAnswer),
                    context.getCancellation()::isCancellationRequested
            );
            context.checkCancellation();
            finishAgentRun(context, outcome, streamedAnswer);
            if ("INPUT_REQUIRED".equalsIgnoreCase(outcome.getStatus())) {
                context.publishClarificationEvent(
                        ConversationEventSources.AI_AGENT,
                        ConversationEventPhases.READY,
                        context.getRenderedAnswer()
                );
                log.info("对话流等待用户补充输入，context={}, durationMs={}",
                        context, System.currentTimeMillis() - startedAt);
                return buildQueryResponse(context);
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
            if (context.getCancellation().isCancellationRequested() || Thread.currentThread().isInterrupted()) {
                markRoundCancelled(context);
                log.info("Agent Runtime 已响应取消信号，context={}, durationMs={}",
                        context, System.currentTimeMillis() - startedAt);
                throw new ConversationCancelledException();
            }
            markRoundFailed(context);
            historyRecorder.saveFailureMessage(context, ex.getMessage());
            context.publishErrorEvent(
                    ConversationEventSources.CONVERSATION,
                    ConversationEventPhases.FAILED,
                    ex.getMessage()
            );
            log.warn("对话流业务链路执行异常，context={}, durationMs={}, error={}",
                    context, System.currentTimeMillis() - startedAt, ex.getMessage());
            throw ex;
        }
    }

    @Override
    public List<ConversationQueryStreamEvent> replayStream(ConversationStreamReconnectRequest request,
                                                           Long userId,
                                                           String traceId) {
        ConversationSessionDTO session = loadSession(request == null ? null : request.getSessionCode(), userId);
        if (session == null) {
            throw BizException.of(AiChatBizCodeConstant.CONVERSATION_NOT_FOUND);
        }
        ConversationRoundDTO round = loadRound(request == null ? null : request.getRoundCode(), session.getSessionCode(), userId);
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
            events.add(buildErrorEvent(traceId, session, round, "agent execution failed"));
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
        return context;
    }

    private AgentConversationRequest buildAgentRequest(ConversationRuntimeContext context) {
        ConversationQueryCommand command = context.getCommand();
        AgentConversationRequest request = new AgentConversationRequest();
        request.setRequestId(command.getTraceId());
        request.setTraceId(command.getTraceId());
        request.setSessionCode(context.getSession().getSessionCode());
        request.setRoundCode(context.getRound().getRoundCode());
        request.setUserId(context.getSession().getUserId());
        request.setModelId(command.getModelId());
        request.setInput(command.getMessage());
        request.setTarget(StringUtils.hasText(command.getAgentCode())
                ? AgentTarget.explicit(command.getAgentCode(), command.getAgentVersion())
                : new AgentTarget(AgentTarget.TYPE_AGENT,
                        StringUtils.hasText(command.getAgentEntryCode()) ? command.getAgentEntryCode() : "HOME_CHAT",
                        null,
                        null));
        ConversationMessageDTO current = context.getOrCreateUserMessageContext().getCurrentMessage();
        request.setMessages(toAgentMessages(
                context.getOrCreateUserMessageContext().getSessionMessages(),
                current == null ? null : current.getMessageCode()));
        Map<String, Object> runContext = new LinkedHashMap<>();
        runContext.put("scene", command.getScene());
        runContext.put("userId", context.getSession().getUserId());
        if (command.getExt() != null && command.getExt().get("clientContext") instanceof Map<?, ?> clientContext) {
            runContext.put("clientContext", clientContext);
        }
        runContext.put("knowledgeBases", kbStoreService.availableKnowledgeBases());
        request.setContext(runContext);
        return request;
    }

    private List<ChatMessage> toAgentMessages(List<ConversationMessageDTO> messages, String currentMessageCode) {
        if (messages == null) {
            return List.of();
        }
        List<ChatMessage> candidates = messages.stream()
                .filter(message -> message != null && StringUtils.hasText(message.getContent()))
                .filter(message -> !StringUtils.hasText(currentMessageCode)
                        || !currentMessageCode.equals(message.getMessageCode()))
                .map(this::toAgentMessage)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<ChatMessage> selected = new ArrayList<>();
        int characters = 0;
        for (int index = candidates.size() - 1; index >= 0 && selected.size() < MAX_HISTORY_MESSAGES; index--) {
            ChatMessage candidate = candidates.get(index);
            int length = candidate.getContent() == null ? 0 : candidate.getContent().length();
            if (!selected.isEmpty() && characters + length > MAX_HISTORY_CHARACTERS) {
                break;
            }
            selected.add(0, candidate);
            characters += length;
        }
        return selected;
    }

    private ChatMessage toAgentMessage(ConversationMessageDTO source) {
        MessageRole role;
        if ("USER".equalsIgnoreCase(source.getRole())) {
            role = MessageRole.USER;
        } else if ("ASSISTANT".equalsIgnoreCase(source.getRole())) {
            role = MessageRole.ASSISTANT;
        } else if ("SYSTEM".equalsIgnoreCase(source.getRole())) {
            role = MessageRole.SYSTEM;
        } else if ("TOOL".equalsIgnoreCase(source.getRole())) {
            role = MessageRole.TOOL;
        } else {
            return null;
        }
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(source.getContent());
        return message;
    }

    private void handleAgentEvent(ConversationRuntimeContext context,
                                  AgentRunEvent event,
                                  StringBuilder streamedAnswer) {
        if (event == null) {
            return;
        }
        context.checkCancellation();
        String eventType = StringUtils.hasText(event.getEventType()) ? event.getEventType().trim() : "agent.updated";
        String delta = event.getDelta();
        Map<String, Object> ext = new LinkedHashMap<>(event.getExt() == null ? Map.of() : event.getExt());
        putIfPresent(ext, "runId", event.getRunId());
        putIfPresent(ext, "agentCode", event.getAgentCode());
        putIfPresent(ext, "agentVersion", event.getAgentVersion());
        putIfPresent(ext, "agentName", event.getAgentName());
        putIfPresent(ext, "timestamp", event.getTimestamp());

        if ("assistant.message.delta".equalsIgnoreCase(eventType) || "answer_delta".equalsIgnoreCase(eventType)) {
            if (StringUtils.hasText(delta)) {
                streamedAnswer.append(delta);
                context.publishEvent("answer_delta", ConversationEventSources.AI_AGENT,
                        phase(eventType, event.getStatus()), event.getMessage(), streamedAnswer.toString(), delta,
                        defaultStatus(event.getStatus()), ext);
            }
            return;
        }
        historyRecorder.saveActivity(
                context,
                ConversationEventSources.AI_AGENT,
                phase(eventType, event.getStatus()),
                event.getMessage(),
                defaultStatus(event.getStatus()),
                ext
        );
        context.publishEvent(eventType, ConversationEventSources.AI_AGENT,
                phase(eventType, event.getStatus()), event.getMessage(), null, null,
                defaultStatus(event.getStatus()), ext);
    }

    private void finishAgentRun(ConversationRuntimeContext context,
                                AgentConversationOutcome outcome,
                                StringBuilder streamedAnswer) {
        if (outcome == null) {
            throw BizException.of(AiChatBizCodeConstant.AGENT_EXECUTION_FAILED, "Agent runtime returned no result");
        }
        String answer = StringUtils.hasText(outcome.getAnswer()) ? outcome.getAnswer() : streamedAnswer.toString();
        if (!StringUtils.hasText(answer)) {
            throw BizException.of(AiChatBizCodeConstant.AGENT_EXECUTION_FAILED, "Agent runtime returned an empty answer");
        }
        context.setRenderedAnswer(answer);
        ConversationMessageDTO current = context.getOrCreateUserMessageContext().getCurrentMessage();
        ConversationMessageDTO assistant = historyRecorder.saveMessage(
                context,
                context.getRound().getRoundCode(),
                "ASSISTANT",
                ConversationActorType.AI.name(),
                "INPUT_REQUIRED".equalsIgnoreCase(outcome.getStatus())
                        ? ConversationMessageType.ASSISTANT_QUESTION.name()
                        : ConversationMessageType.FINAL_ANSWER.name(),
                answer,
                ConversationContentFormat.MARKDOWN.name(),
                ConversationDisplayLevel.VISIBLE.name(),
                "INPUT_REQUIRED".equalsIgnoreCase(outcome.getStatus()) ? "INPUT_REQUIRED" : "SUCCESS",
                current == null ? null : current.getMessageCode(),
                current == null ? null : current.getMessageCode(),
                agentTrace(outcome)
        );
        persistArtifacts(context, outcome, assistant);
        updateRoundAgentSnapshot(context, outcome);
        if (!"INPUT_REQUIRED".equalsIgnoreCase(outcome.getStatus())) {
            context.publishEvent("answer", ConversationEventSources.AI_AGENT, ConversationEventPhases.READY,
                    "agent final answer", answer, null, "SUCCESS", agentTrace(outcome));
        }
    }

    private void persistArtifacts(ConversationRuntimeContext context,
                                  AgentConversationOutcome outcome,
                                  ConversationMessageDTO assistant) {
        if (outcome.getArtifacts() == null) {
            return;
        }
        for (Map<String, Object> artifact : outcome.getArtifacts()) {
            if (artifact == null) {
                continue;
            }
            String artifactType = text(artifact.get("artifactType"), artifact.get("type"), "AGENT_OUTPUT");
            String contentFormat = text(artifact.get("contentFormat"), artifact.get("format"), "JSON");
            Object content = artifact.containsKey("content") ? artifact.get("content") : artifact;
            boolean visible = persistedArtifactVisible(
                    artifact,
                    content,
                    assistant == null ? null : assistant.getContent()
            );
            historyRecorder.saveArtifact(
                    context,
                    artifactType,
                    text(artifact.get("stage"), "FINAL"),
                    text(artifact.get("title"), artifact.get("artifactCode"), artifactType),
                    content,
                    contentFormat,
                    visible,
                    text(artifact.get("status"), "SUCCESS"),
                    assistant == null ? null : assistant.getMessageCode(),
                    agentTrace(outcome)
            );
        }
    }

    static boolean persistedArtifactVisible(Map<String, Object> artifact,
                                            Object content,
                                            String assistantAnswer) {
        return !Boolean.FALSE.equals(artifact.get("visible"))
                && !duplicatesAssistantFinalAnswer(artifact, content, assistantAnswer);
    }

    private static boolean duplicatesAssistantFinalAnswer(Map<String, Object> artifact,
                                                           Object content,
                                                           String assistantAnswer) {
        String logicalCode = text(
                artifact.get("artifactCode"),
                artifact.get("code"),
                artifact.get("title")
        );
        if (!isFinalAnswerCode(logicalCode)) {
            return false;
        }
        String artifactContent = comparableText(content);
        String assistantContent = comparableText(assistantAnswer);
        return StringUtils.hasText(artifactContent)
                && StringUtils.hasText(assistantContent)
                && artifactContent.equals(assistantContent);
    }

    private static boolean isFinalAnswerCode(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String canonical = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');
        return "final-answer".equals(canonical);
    }

    private static String comparableText(Object value) {
        if (!(value instanceof CharSequence content)) {
            return null;
        }
        return content.toString().replace("\r\n", "\n").trim();
    }

    private void updateRoundAgentSnapshot(ConversationRuntimeContext context, AgentConversationOutcome outcome) {
        ConversationRoundDTO round = context.getRound();
        ConversationRoundDTO update = new ConversationRoundDTO();
        String status = "INPUT_REQUIRED".equalsIgnoreCase(outcome.getStatus()) ? "INPUT_REQUIRED" : "SUCCESS";
        update.setStatus(status);
        update.setModelCode(outcome.getModelCode());
        update.setActualModel(outcome.getActualModel());
        update.setAgentRunId(outcome.getRunId());
        update.setRootAgentCode(outcome.getRootAgentCode());
        update.setRootAgentVersion(outcome.getRootAgentVersion());
        update.setAgentRuntimeType(outcome.getRuntimeType() == null ? null : outcome.getRuntimeType().name());
        update.setAgentSdkVersion(outcome.getSdkVersion());
        update.setAgentSnapshotHash(outcome.getSnapshotHash());
        roundService.edit(round.getId(), update);
        round.setStatus(status);
        round.setModelCode(update.getModelCode());
        round.setActualModel(update.getActualModel());
        round.setAgentRunId(update.getAgentRunId());
        round.setRootAgentCode(update.getRootAgentCode());
        round.setRootAgentVersion(update.getRootAgentVersion());
        round.setAgentRuntimeType(update.getAgentRuntimeType());
        round.setAgentSdkVersion(update.getAgentSdkVersion());
        round.setAgentSnapshotHash(update.getAgentSnapshotHash());
    }

    private String phase(String eventType, String status) {
        String value = (eventType + " " + status).toLowerCase();
        if (value.contains("fail") || value.contains("error")) {
            return ConversationEventPhases.FAILED;
        }
        if (value.contains("complete")) {
            return ConversationEventPhases.COMPLETED;
        }
        if (value.contains("start")) {
            return ConversationEventPhases.STARTED;
        }
        return ConversationEventPhases.RUNNING;
    }

    private String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status : "RUNNING";
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String text) || StringUtils.hasText(text))) {
            target.put(key, value);
        }
    }

    private Map<String, Object> agentTrace(AgentConversationOutcome outcome) {
        Map<String, Object> trace = new LinkedHashMap<>();
        if (outcome != null) {
            putIfPresent(trace, "runId", outcome.getRunId());
            putIfPresent(trace, "agentCode", outcome.getRootAgentCode());
            putIfPresent(trace, "agentVersion", outcome.getRootAgentVersion());
            putIfPresent(trace, "snapshotHash", outcome.getSnapshotHash());
        }
        return trace;
    }

    private static String text(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
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

        ConversationMessageDTO currentMessage = context.getOrCreateUserMessageContext().getCurrentMessage();
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
        response.setFinishReason(response.getStatus());
        return response;
    }

    private void markRoundFailed(ConversationRuntimeContext context) {
        ConversationRoundDTO round = context == null ? null : context.getRound();
        if (round == null || round.getId() == null) {
            return;
        }
        ConversationRoundDTO update = new ConversationRoundDTO();
        update.setStatus("FAILED");
        roundService.edit(round.getId(), update);
        round.setStatus("FAILED");
    }

    private void markRoundCancelled(ConversationRuntimeContext context) {
        ConversationRoundDTO round = context == null ? null : context.getRound();
        if (round == null || round.getId() == null) {
            return;
        }
        ConversationRoundDTO update = new ConversationRoundDTO();
        update.setStatus("CANCELLED");
        roundService.edit(round.getId(), update);
        round.setStatus("CANCELLED");
    }

    private ConversationSessionDTO loadSession(String sessionCode, Long userId) {
        if (!StringUtils.hasText(sessionCode)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_SESSION_CODE);
        }
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return sessionService.get(query);
    }

    private ConversationRoundDTO loadRound(String roundCode, String sessionCode, Long userId) {
        if (!StringUtils.hasText(roundCode)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_ROUND_CODE);
        }
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        query.setRoundCode(roundCode);
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return roundService.queryAll(query).stream()
                .max(Comparator.comparing(ConversationRoundDTO::getId, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
    }

    private String loadLatestAssistantAnswer(String roundCode, String sessionCode, Long userId) {
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        query.setRoundCode(roundCode);
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        List<ConversationMessageDTO> messages = messageService.queryAll(query).stream()
                .filter(message -> message != null && StringUtils.hasText(message.getContent()))
                .filter(message -> "ASSISTANT".equalsIgnoreCase(message.getRole()))
                .sorted(Comparator.comparing(ConversationMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        return messages.isEmpty() ? null : messages.get(messages.size() - 1).getContent();
    }

    private ConversationQueryStreamEvent buildInitEvent(String traceId,
                                                        ConversationSessionDTO session,
                                                        ConversationRoundDTO round) {
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
                                                             ConversationSessionDTO session,
                                                             ConversationRoundDTO round,
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
                                                            ConversationSessionDTO session,
                                                            ConversationRoundDTO round,
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
                                                         ConversationSessionDTO session,
                                                         ConversationRoundDTO round,
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
                                                             ConversationSessionDTO session,
                                                             ConversationRoundDTO round) {
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
