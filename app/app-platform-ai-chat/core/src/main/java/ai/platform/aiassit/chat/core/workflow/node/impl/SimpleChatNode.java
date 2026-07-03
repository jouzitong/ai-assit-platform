package ai.platform.aiassit.chat.core.workflow.node.impl;

import ai.platform.aiassist.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassist.service.ai.api.dto.ChatMessage;
import ai.platform.aiassist.service.ai.api.dto.ChatOptions;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.IntentAnalyzeResponse;
import ai.platform.aiassist.service.ai.api.dto.OutputItem;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassist.service.ai.api.enums.MessageRole;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import ai.platform.aiassit.chat.core.workflow.support.WorkflowHistoryRecorder;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.enums.AiChatActorType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import ai.platform.aiassit.chat.history.enums.AiChatDisplayLevel;
import ai.platform.aiassit.chat.history.enums.AiChatMessageType;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class SimpleChatNode extends BaseWorkflowNode {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DEFAULT_SCENE = "ai-chat-simple-chat";
    private static final String SIMPLE_CHAT_PROMPT = """
            你是一个简洁、可靠的中文 AI 助手。
            当前对话已经被判定为 SIMPLE_CHAT。

            要求：
            1. 直接回答用户问题，不要进入查数、SQL、报表或 render-json 风格表达
            2. 如果用户是在追问历史上下文，要结合历史消息保持语义连续
            3. 回答尽量清晰、自然、简洁，避免无根据编造
            4. 只输出给用户看的最终回答正文
            """;

    private final AiChatExecutionApi aiChatExecutionApi;
    private final AiChatRoundService roundService;
    private final WorkflowHistoryRecorder historyRecorder;

    public SimpleChatNode(AiChatExecutionApi aiChatExecutionApi,
                          AiChatRoundService roundService,
                          WorkflowHistoryRecorder historyRecorder) {
        this.aiChatExecutionApi = aiChatExecutionApi;
        this.roundService = roundService;
        this.historyRecorder = historyRecorder;
    }

    @Override
    protected NodeResult doExecute(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        if (command == null) {
            return NodeResult.fail("command is required");
        }
        if (context.getRound() == null) {
            return NodeResult.fail("round is required");
        }
        try {
            ChatRequest request = buildRequest(context);
            context.getOrCreateNodeResult(WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode()).setRequest(request);

            ChatResponse response = aiChatExecutionApi.chat(request).getData();
            context.getOrCreateNodeResult(WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode()).setResponse(response);

            String answer = extractAnswer(response);
            if (!StringUtils.hasText(answer)) {
                throw new IllegalArgumentException("simple chat answer is empty");
            }

            context.setRenderedAnswer(answer);
            context.getOrCreateNodeResult(WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode()).setStatus(STATUS_SUCCESS);
            context.putNodeOutput(WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode(), "requestId",
                    response == null ? null : response.getRequestId());
            context.publishEvent("answer-ready", "simple chat answer prepared", answer, null, STATUS_SUCCESS);

            persistAssistantMessage(context, answer);
            finishRound(context.getRound(), STATUS_SUCCESS, response == null ? request.getModel() : response.getModel(), request);
            return NodeResult.success(null);
        } catch (Exception ex) {
            context.getOrCreateNodeResult(WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode()).setStatus(STATUS_FAILED);
            finishRound(context.getRound(), STATUS_FAILED, null, null);
            return NodeResult.fail(ex.getMessage());
        }
    }

    @Override
    public String code() {
        return WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode();
    }

    @Override
    public int order() {
        return 650;
    }

    private ChatRequest buildRequest(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        ChatRequest request = new ChatRequest();
        request.setProvider(null);
        request.setModel(command == null ? null : command.getApiModel());
        request.setMessages(buildMessages(context));

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(2048);
        options.setTimeoutMs(20_000);
        request.setOptions(options);

        RequestMeta meta = new RequestMeta();
        meta.setTraceId(command == null ? null : command.getTraceId());
        meta.setScene(StringUtils.hasText(command == null ? null : command.getScene())
                ? command.getScene() + "-simple-chat"
                : DEFAULT_SCENE);
        request.setMeta(meta);
        return request;
    }

    private List<ChatMessage> buildMessages(WorkflowContext context) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(buildMessage(MessageRole.SYSTEM, SIMPLE_CHAT_PROMPT));

        String intentSummary = buildIntentSummary(context);
        if (StringUtils.hasText(intentSummary)) {
            messages.add(buildMessage(MessageRole.SYSTEM, intentSummary));
        }

        List<AiChatMessageDTO> historyMessages = resolveHistoryMessages(context);
        for (AiChatMessageDTO historyMessage : historyMessages) {
            ChatMessage message = toChatMessage(historyMessage);
            if (message != null) {
                messages.add(message);
            }
        }

        AiChatMessageDTO currentMessage = context.getOrCreateUserMessageContext().getCurrentMessage();
        messages.add(buildMessage(MessageRole.USER,
                currentMessage == null ? context.getCommand().getMessage() : currentMessage.getContent()));
        return messages;
    }

    private String buildIntentSummary(WorkflowContext context) {
        IntentAnalyzeResponse response = context.get(WorkflowContextKeys.Planning.INTENT_ANALYZE_RESPONSE);
        if (response == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(response.getIntentType())) {
            parts.add("intentType=" + response.getIntentType());
        }
        if (StringUtils.hasText(response.getSummary())) {
            parts.add("summary=" + response.getSummary());
        }
        if (!CollectionUtils.isEmpty(response.getImportantInfos())) {
            parts.add("importantInfos=" + response.getImportantInfos());
        }
        return parts.isEmpty() ? null : "基础意图分析结论：" + String.join("；", parts);
    }

    private List<AiChatMessageDTO> resolveHistoryMessages(WorkflowContext context) {
        List<AiChatMessageDTO> sessionMessages = context.getOrCreateUserMessageContext().getSessionMessages();
        AiChatMessageDTO currentMessage = context.getOrCreateUserMessageContext().getCurrentMessage();
        if (CollectionUtils.isEmpty(sessionMessages)) {
            return List.of();
        }
        return sessionMessages.stream()
                .filter(Objects::nonNull)
                .filter(message -> currentMessage == null
                        || !Objects.equals(message.getMessageCode(), currentMessage.getMessageCode()))
                .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private ChatMessage toChatMessage(AiChatMessageDTO messageDTO) {
        if (messageDTO == null || !StringUtils.hasText(messageDTO.getContent())) {
            return null;
        }
        return buildMessage(resolveRole(messageDTO.getRole()), messageDTO.getContent().trim());
    }

    private MessageRole resolveRole(String role) {
        if (!StringUtils.hasText(role)) {
            return MessageRole.USER;
        }
        try {
            return MessageRole.valueOf(role.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ex) {
            return MessageRole.USER;
        }
    }

    private ChatMessage buildMessage(MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private String extractAnswer(ChatResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getOutputs())) {
            return "";
        }
        return response.getOutputs().stream()
                .filter(Objects::nonNull)
                .map(OutputItem::getText)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private void persistAssistantMessage(WorkflowContext context, String answer) {
        historyRecorder.saveMessage(
                context,
                context.getRound().getRoundCode(),
                "ASSISTANT",
                AiChatActorType.AI.name(),
                AiChatMessageType.FINAL_ANSWER.name(),
                answer,
                AiChatContentFormat.PLAIN_TEXT.name(),
                AiChatDisplayLevel.VISIBLE.name(),
                STATUS_SUCCESS,
                context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null
                        : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null
                        : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                null
        );
    }

    private void finishRound(AiChatRoundDTO round, String status, String actualModel, ChatRequest request) {
        if (round == null || round.getId() == null) {
            return;
        }
        AiChatRoundDTO update = new AiChatRoundDTO();
        update.setStatus(status);
        update.setActualModel(actualModel);
        if (request != null) {
            update.setModelCode(request.getModel());
        }
        roundService.edit(round.getId(), update);
    }
}
