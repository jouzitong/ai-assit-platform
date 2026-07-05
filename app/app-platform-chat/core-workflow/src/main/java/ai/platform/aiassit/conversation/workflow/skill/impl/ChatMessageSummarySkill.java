package ai.platform.aiassit.conversation.workflow.skill.impl;

import ai.platform.aiassit.service.ai.api.dto.ChatMessage;
import ai.platform.aiassit.service.ai.api.dto.ChatOptions;
import ai.platform.aiassit.service.ai.api.dto.ChatRequest;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.dto.OutputItem;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.enums.MessageRole;
import ai.platform.aiassit.service.ai.api.enums.ProviderType;
import ai.platform.aiassit.conversation.workflow.constants.AiChatQueryExtKeys;
import ai.platform.aiassit.conversation.dto.chat.AiChatQueryCommand;
import ai.platform.aiassit.conversation.workflow.bean.NodeResult;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowSkillPhase;
import ai.platform.aiassit.conversation.workflow.context.InvalidIntentItem;
import ai.platform.aiassit.conversation.workflow.context.UserMessageContext;
import ai.platform.aiassit.conversation.workflow.context.WorkflowContext;
import ai.platform.aiassit.conversation.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.conversation.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.conversation.workflow.skill.IWorkflowNodeSkill;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import ai.platform.aiassit.execution.service.AiExecutionDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 聊天消息摘要技能。
 *
 * <p>基于当前用户消息与历史用户消息，调用 AI 提炼本轮有效需求摘要。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/22
 */
@Component
public class ChatMessageSummarySkill implements IWorkflowNodeSkill {

    private static final String DEFAULT_SCENE = "ai-chat-message-summary";
    private static final String SYSTEM_PROMPT = """
            你是对话需求提炼助手。
            请根据当前用户消息和历史用户消息，对用户的真实意图做一次简洁总结。

            要求：
            1. 核心是提炼用户真实意图，去掉寒暄、重复表达、无关噪声
            2. 如果历史消息中存在 A-B-A 这类变化，应识别最终有效诉求，去除中间已失效干扰信息
            3. 如果历史消息和最后一条消息存在变化，以最后一条消息表达的有效诉求为准
            4. 输出 JSON，结构固定为：
               {
                 "title": "6到20字的会话标题",
                 "summary": "中文摘要",
                 "invalidIntents": [
                   {
                     "content": "已失效意图内容",
                     "reason": "判定其失效的原因",
                     "evidence": "判定其失效的直接依据"
                   }
                 ]
               }
            5. 如果没有已失效意图，invalidIntents 返回空数组
            6. 不要输出 JSON 之外的任何解释
            """;

    private final AiExecutionDomainService aiExecutionDomainService;
    private final AiChatSessionService sessionService;
    private final ObjectMapper objectMapper;

    public ChatMessageSummarySkill(AiExecutionDomainService aiExecutionDomainService,
                                   AiChatSessionService sessionService,
                                   ObjectMapper objectMapper) {
        this.aiExecutionDomainService = aiExecutionDomainService;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String code() {
        return "chat_message_summary";
    }

    @Override
    public WorkflowSkillPhase phase() {
        return WorkflowSkillPhase.AFTER_EXECUTE;
    }

    @Override
    public NodeResult execute(WorkflowContext context, WorkflowNodeConfig nodeConfig, NodeResult nodeResult) {
        UserMessageContext userMessageContext = context.getOrCreateUserMessageContext();
        AiChatMessageDTO currentMessage = userMessageContext.getCurrentMessage();
        if (currentMessage == null || !StringUtils.hasText(currentMessage.getContent())) {
            return NodeResult.success(nodeResult == null ? null : nodeResult.getNextNodeId());
        }

        try {
            ChatRequest request = buildRequest(context, userMessageContext);
            context.getOrCreateNodeResult(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode()).setRequest(request);
            ChatResponse response = aiExecutionDomainService.chat(request);
            context.getOrCreateNodeResult(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode()).setResponse(response);
            ChatMessageSummaryResult summaryResult = parseSummaryResult(extractAnswer(response));
            if (summaryResult != null && StringUtils.hasText(summaryResult.getSummary())) {
                userMessageContext.setSummary(summaryResult.getSummary().trim());
                userMessageContext.setInvalidIntents(summaryResult.getInvalidIntents() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(summaryResult.getInvalidIntents()));
                refreshSessionName(context, summaryResult.getTitle(), summaryResult.getSummary());
                context.putNodeOutput(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode(),
                        "title",
                        trimToNull(summaryResult.getTitle()));
                context.putNodeOutput(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode(),
                        "summary",
                        summaryResult.getSummary().trim());
                context.putNodeOutput(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode(),
                        "invalidIntents",
                        userMessageContext.getInvalidIntents());
            }
        } catch (Exception ex) {
            context.put(WorkflowContextKeys.ChatMessage.SUMMARY_ERROR, ex.getMessage());
        }
        return NodeResult.success(nodeResult == null ? null : nodeResult.getNextNodeId());
    }

    private void refreshSessionName(WorkflowContext context, String title, String summary) {
        AiChatMessageDTO currentMessage = context.getOrCreateUserMessageContext().getCurrentMessage();
        if (context.getSession() == null
                || context.getSession().getId() == null
                || currentMessage == null
                || (!StringUtils.hasText(title) && !StringUtils.hasText(summary))) {
            return;
        }
        boolean firstRound = Integer.valueOf(1).equals(currentMessage.getSortNo());
        if (!firstRound && !allowUpdateSessionName(context.getCommand())) {
            return;
        }
        String sessionName = buildSessionName(title, summary);
        if (!StringUtils.hasText(sessionName)) {
            return;
        }
        AiChatSessionDTO update = new AiChatSessionDTO();
        update.setSessionName(sessionName);
        AiChatSessionDTO updated = sessionService.edit(context.getSession().getId(), update);
        if (updated != null) {
            context.setSession(updated);
        } else {
            context.getSession().setSessionName(sessionName);
        }
    }

    private boolean allowUpdateSessionName(AiChatQueryCommand command) {
        if (command == null || command.getExt() == null || command.getExt().isEmpty()) {
            return false;
        }
        Object value = command.getExt().get(AiChatQueryExtKeys.ALLOW_UPDATE_SESSION_NAME);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return "true".equalsIgnoreCase(str.trim());
        }
        return false;
    }

    private String buildSessionName(String title, String summary) {
        String normalized = trimToNull(title);
        if (!StringUtils.hasText(normalized)) {
            normalized = trimToNull(summary);
        }
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.length() > 20 ? normalized.substring(0, 20) : normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ChatRequest buildRequest(WorkflowContext context, UserMessageContext userMessageContext) {
        AiChatQueryCommand command = context.getCommand();
        ChatRequest request = new ChatRequest();
        request.setProvider(resolveProviderType(command == null ? null : command.getApiModel()));
        request.setModel(resolveActualModel(command == null ? null : command.getApiModel()));
        request.setMessages(buildMessages(userMessageContext));

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(Math.min(resolveMaxTokens(command == null ? null : command.getApiModel()), 1024));
        options.setTimeoutMs(15_000);
        request.setOptions(options);

        RequestMeta meta = new RequestMeta();
        if (command != null) {
            meta.setTraceId(command.getTraceId());
            meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() + "-summary" : DEFAULT_SCENE);
        } else {
            meta.setScene(DEFAULT_SCENE);
        }
        request.setMeta(meta);
        return request;
    }

    private List<ChatMessage> buildMessages(UserMessageContext userMessageContext) {
        List<ChatMessage> messages = new ArrayList<>();

        ChatMessage systemMessage = new ChatMessage();
        systemMessage.setRole(MessageRole.SYSTEM);
        systemMessage.setContent(SYSTEM_PROMPT);
        messages.add(systemMessage);

        StringBuilder builder = new StringBuilder();
        builder.append("当前用户消息：\n")
                .append(userMessageContext.getCurrentMessage().getContent().trim())
                .append("\n\n");

        List<AiChatMessageDTO> historyMessages = resolveHistoricalUserMessages(userMessageContext);
        if (!CollectionUtils.isEmpty(historyMessages)) {
            builder.append("历史用户消息：\n");
            for (int i = 0; i < historyMessages.size(); i++) {
                builder.append(i + 1)
                        .append(". ")
                        .append(historyMessages.get(i).getContent().trim())
                        .append('\n');
            }
        } else {
            builder.append("历史用户消息：无");
        }

        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(builder.toString().trim());
        messages.add(userMessage);
        return messages;
    }

    private List<AiChatMessageDTO> resolveHistoricalUserMessages(UserMessageContext userMessageContext) {
        if (userMessageContext == null || CollectionUtils.isEmpty(userMessageContext.getSessionMessages())) {
            return List.of();
        }
        return userMessageContext.getSessionMessages().stream()
                .filter(Objects::nonNull)
                .filter(message -> "USER".equalsIgnoreCase(message.getRole()))
                .filter(message -> StringUtils.hasText(message.getContent()))
                .filter(message -> userMessageContext.getCurrentMessage() == null
                        || !Objects.equals(message.getMessageCode(), userMessageContext.getCurrentMessage().getMessageCode()))
                .toList();
    }

    private String extractAnswer(ChatResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getOutputs())) {
            return null;
        }
        return response.getOutputs().stream()
                .filter(Objects::nonNull)
                .map(OutputItem::getText)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private ChatMessageSummaryResult parseSummaryResult(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        try {
            ChatMessageSummaryResult result = objectMapper.readValue(cleanJson(rawText), ChatMessageSummaryResult.class);
            if (result.getInvalidIntents() == null) {
                result.setInvalidIntents(new ArrayList<>());
            }
            return result;
        } catch (Exception ex) {
            ChatMessageSummaryResult fallback = new ChatMessageSummaryResult();
            fallback.setSummary(rawText.trim());
            fallback.setInvalidIntents(new ArrayList<>());
            return fallback;
        }
    }

    private String cleanJson(String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim();
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }
        return cleaned;
    }

    private ProviderType resolveProviderType(String apiModel) {
        if (StringUtils.hasText(apiModel) && apiModel.toLowerCase(Locale.ROOT).contains("qwen")) {
            return ProviderType.DASHSCOPE;
        }
        return ProviderType.DASHSCOPE;
    }

    private int resolveMaxTokens(String apiModel) {
        return 512;
    }

    private String resolveActualModel(String apiModel) {
        if (StringUtils.hasText(apiModel)) {
            return apiModel.trim();
        }
        return "qwen-math-turbo";
    }

    @lombok.Data
    private static class ChatMessageSummaryResult {
        private String title;
        private String summary;
        private List<InvalidIntentItem> invalidIntents = new ArrayList<>();
    }
}
