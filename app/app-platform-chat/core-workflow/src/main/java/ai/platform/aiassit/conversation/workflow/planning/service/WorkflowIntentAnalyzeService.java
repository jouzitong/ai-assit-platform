package ai.platform.aiassit.conversation.workflow.planning.service;

import ai.platform.aiassit.service.ai.api.dto.ChatMessage;
import ai.platform.aiassit.service.ai.api.dto.ChatOptions;
import ai.platform.aiassit.service.ai.api.dto.ChatRequest;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.dto.IntentAnalyzeResponse;
import ai.platform.aiassit.service.ai.api.dto.OutputItem;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.dto.ResponseFormat;
import ai.platform.aiassit.service.ai.api.enums.MessageRole;
import ai.platform.aiassit.service.ai.api.enums.OutputType;
import ai.platform.aiassit.service.ai.api.enums.ResponseFormatType;
import ai.platform.aiassit.execution.service.AiExecutionDomainService;
import ai.platform.aiassit.conversation.workflow.dto.chat.AiChatQueryCommand;
import ai.platform.aiassit.conversation.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.conversation.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工作流基础意图分析服务。
 *
 * <p>在正式进入 QueryPlanningNode 之前，先用基础系统描述生成一份轻量意图结论，
 * 供工作流引擎预热上下文和后续规划技能复用。</p>
 */
@Component
public class WorkflowIntentAnalyzeService {

    private static final String DEFAULT_SCENE = "ai-chat-base-intent-analyze";

    private static final String INTENT_ANALYZE_PROMPT = """
            你是 AI 问答工作流的基础意图分析助手。
            请根据当前用户问题、历史对话和补充上下文，先做一轮轻量但可靠的意图预分析。

            你的目标不是直接产出最终查询规划，而是先给出稳定的基础结论，供后续 Planning 使用。
            你只能做基础意图分析，不要输出指标、维度、数据集、时间范围、SQL 规划等下游结构。

            你必须只返回严格合法的 JSON，对象结构固定如下：
            {
              "intentType": "意图类型，仅允许 SIMPLE_CHAT 或 QUERY_RENDER",
              "rewrittenQuery": "归一化后的用户问题",
              "summary": "用户需求摘要，突出真正想解决的问题",
              "sessionTitle": "适合给 session 使用的短标题",
              "typoCorrected": false,
              "corrections": ["错别字1 -> 正确表达1"],
              "risk": {
                "level": "LOW",
                "summary": "整体风险总结",
                "items": [
                  {
                    "type": "TYPO",
                    "description": "风险描述",
                    "evidence": "判断依据",
                    "score": 0.85
                  }
                ]
              },
              "score": 0.86,
              "invalidIntentSummary": "对历史已失效意图的总结描述；如果没有则返回空字符串",
              "invalidIntents": [
                {
                  "content": "已失效的旧观点",
                  "reason": "为什么失效",
                  "evidence": "失效依据内容",
                  "score": 0.91
                }
              ],
              "clarificationNeeded": false,
              "clarificationQuestion": "若需要追问，返回最关键的一句；否则返回空字符串"
            }

            字段要求：
            1. intentType、rewrittenQuery、summary、sessionTitle、clarificationQuestion、invalidIntentSummary 必须返回字符串
            2. corrections、invalidIntents、risk.items 必须返回 JSON 数组，可为空数组
            3. typoCorrected、clarificationNeeded 必须返回布尔值
            4. risk、invalidIntents 中的 score 以及顶层 score 必须返回 0~1 的数字
            5. risk 描述的是“分析风险”，例如错别字、歧义、上下文冲突、信息缺失，而不是业务风险
            6. rewrittenQuery 允许纠正错别字、口语、省略和代词，但不要扩展成查询规划
            7. invalidIntentSummary 和 invalidIntents 只描述“历史理解里哪些内容已经失效”
            8. intentType 取值规则：
               - SIMPLE_CHAT：普通闲聊、解释、问答、建议，不需要进入查数渲染链路
               - QUERY_RENDER：用户核心目标是查数据、分析数据、生成报表、做指标解释或需要进入查询渲染链路
            9. 如果无法绝对确认，但问题明显偏向数据查询/分析，优先返回 QUERY_RENDER
            10. 不要输出 JSON 之外的任何解释
            """;

    private final AiExecutionDomainService aiExecutionDomainService;
    private final ObjectMapper objectMapper;

    public WorkflowIntentAnalyzeService(AiExecutionDomainService aiExecutionDomainService,
                                        ObjectMapper objectMapper) {
        this.aiExecutionDomainService = aiExecutionDomainService;
        this.objectMapper = objectMapper;
    }

    public IntentAnalyzeResponse analyze(WorkflowContext context) {
        AiChatQueryCommand command = context == null ? null : context.getCommand();
        if (command == null || !StringUtils.hasText(command.getMessage())) {
            return null;
        }
        ChatRequest request = buildRequest(context);
        ChatResponse result = aiExecutionDomainService.chat(request);
        if (result == null) {
            throw new IllegalStateException("base intent analyze failed");
        }
        IntentAnalyzeResponse response = parseResponse(result, command);
        response.setRequestId(result.getRequestId());
        response.setModel(result.getModel());
        normalizeResponse(response, command);
        return response;
    }

    private ChatRequest buildRequest(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        ChatRequest request = new ChatRequest();
        request.setProvider(null);
        request.setModel(command.getApiModel());
        request.setMessages(buildMessages(context));
        request.setResponseFormat(buildResponseFormat());

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(1024);
        options.setTimeoutMs(20_000);
        request.setOptions(options);

        RequestMeta meta = new RequestMeta();
        meta.setTraceId(command.getTraceId());
        meta.setTenantId(command.getUserId() == null ? null : String.valueOf(command.getUserId()));
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() + "-base-intent" : DEFAULT_SCENE);
        request.setMeta(meta);
        if (command.getExt() != null && !command.getExt().isEmpty()) {
            request.setExt(new java.util.HashMap<>(command.getExt()));
        }
        return request;
    }

    private List<ChatMessage> buildMessages(WorkflowContext context) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(buildMessage(MessageRole.SYSTEM, INTENT_ANALYZE_PROMPT));

        String contextText = buildContextText(context);
        if (StringUtils.hasText(contextText)) {
            messages.add(buildMessage(MessageRole.SYSTEM, contextText));
        }

        List<AiChatMessageDTO> historyMessages = resolveHistoryMessages(context);
        for (AiChatMessageDTO historyMessage : historyMessages) {
            ChatMessage message = toChatMessage(historyMessage);
            if (message != null) {
                messages.add(message);
            }
        }

        messages.add(buildMessage(
                MessageRole.USER,
                context.getOrCreateUserMessageContext().getCurrentMessage() == null
                        ? context.getCommand().getMessage()
                        : context.getOrCreateUserMessageContext().getCurrentMessage().getContent()
        ));
        return messages;
    }

    private String buildContextText(WorkflowContext context) {
        StringBuilder builder = new StringBuilder();
        AiChatQueryCommand command = context.getCommand();
        if (command != null && command.getBusinessType() != null) {
            builder.append("请求入参业务类型：").append(command.getBusinessType()).append('\n');
        }
        String userMessageSummary = context.getOrCreateUserMessageContext().getSummary();
        if (StringUtils.hasText(userMessageSummary)) {
            builder.append("用户消息上下文汇总：").append('\n').append(userMessageSummary).append('\n');
        }
        Object normalizedTimeRange = context.get(WorkflowContextKeys.Skill.NORMALIZED_TIME_RANGE);
        if (normalizedTimeRange != null) {
            builder.append("已标准化时间范围：").append(normalizedTimeRange).append('\n');
        }
        List<String> resolvedTerms = context.get(WorkflowContextKeys.Skill.RESOLVED_BUSINESS_TERMS);
        if (!CollectionUtils.isEmpty(resolvedTerms)) {
            builder.append("已识别业务术语：").append(resolvedTerms).append('\n');
        }
        return builder.toString().trim();
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
        ChatMessage message = new ChatMessage();
        message.setRole(resolveRole(messageDTO.getRole()));
        message.setContent(messageDTO.getContent().trim());
        return message;
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

    private ResponseFormat buildResponseFormat() {
        ResponseFormat responseFormat = new ResponseFormat();
        responseFormat.setType(ResponseFormatType.JSON_SCHEMA);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("intentType", Map.of("type", "string"));
        properties.put("rewrittenQuery", Map.of("type", "string"));
        properties.put("summary", Map.of("type", "string"));
        properties.put("sessionTitle", Map.of("type", "string"));
        properties.put("typoCorrected", Map.of("type", "boolean"));
        properties.put("corrections", buildStringArraySchema());
        properties.put("risk", buildRiskSchema());
        properties.put("score", Map.of("type", "number"));
        properties.put("invalidIntentSummary", Map.of("type", "string"));
        properties.put("invalidIntents", buildInvalidIntentArraySchema());
        properties.put("clarificationNeeded", Map.of("type", "boolean"));
        properties.put("clarificationQuestion", Map.of("type", "string"));
        schema.put("properties", properties);
        schema.put("required", List.of(
                "intentType",
                "rewrittenQuery",
                "summary",
                "sessionTitle",
                "typoCorrected",
                "corrections",
                "risk",
                "score",
                "invalidIntentSummary",
                "invalidIntents",
                "clarificationNeeded",
                "clarificationQuestion"
        ));
        responseFormat.setSchema(schema);
        return responseFormat;
    }

    private Map<String, Object> buildStringArraySchema() {
        return Map.of(
                "type", "array",
                "items", Map.of("type", "string")
        );
    }

    private Map<String, Object> buildRiskSchema() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("type", Map.of("type", "string"));
        itemProperties.put("description", Map.of("type", "string"));
        itemProperties.put("evidence", Map.of("type", "string"));
        itemProperties.put("score", Map.of("type", "number"));

        Map<String, Object> riskItemSchema = new LinkedHashMap<>();
        riskItemSchema.put("type", "object");
        riskItemSchema.put("additionalProperties", false);
        riskItemSchema.put("properties", itemProperties);
        riskItemSchema.put("required", List.of("type", "description", "evidence", "score"));

        Map<String, Object> riskProperties = new LinkedHashMap<>();
        riskProperties.put("level", Map.of("type", "string"));
        riskProperties.put("summary", Map.of("type", "string"));
        riskProperties.put("items", Map.of("type", "array", "items", riskItemSchema));

        Map<String, Object> riskSchema = new LinkedHashMap<>();
        riskSchema.put("type", "object");
        riskSchema.put("additionalProperties", false);
        riskSchema.put("properties", riskProperties);
        riskSchema.put("required", List.of("level", "summary", "items"));
        return riskSchema;
    }

    private Map<String, Object> buildInvalidIntentArraySchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("content", Map.of("type", "string"));
        properties.put("reason", Map.of("type", "string"));
        properties.put("evidence", Map.of("type", "string"));
        properties.put("score", Map.of("type", "number"));

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);
        itemSchema.put("properties", properties);
        itemSchema.put("required", List.of("content", "reason", "evidence", "score"));
        return Map.of("type", "array", "items", itemSchema);
    }

    private IntentAnalyzeResponse parseResponse(ChatResponse response, AiChatQueryCommand command) {
        String rawOutput = extractOutput(response);
        if (!StringUtils.hasText(rawOutput)) {
            IntentAnalyzeResponse fallback = new IntentAnalyzeResponse();
            fallback.setRawOutput(null);
            fallback.setRewrittenQuery(command.getMessage());
            fallback.setSummary(command.getMessage());
            fallback.setSessionTitle(command.getMessage());
            return fallback;
        }
        try {
            IntentAnalyzeResponse result = objectMapper.readValue(cleanJson(rawOutput), IntentAnalyzeResponse.class);
            result.setRawOutput(rawOutput);
            return result;
        } catch (Exception ex) {
            IntentAnalyzeResponse fallback = new IntentAnalyzeResponse();
            fallback.setRawOutput(rawOutput);
            fallback.setRewrittenQuery(command.getMessage());
            fallback.setSummary(rawOutput.trim());
            fallback.setSessionTitle(command.getMessage());
            return fallback;
        }
    }

    private String extractOutput(ChatResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getOutputs())) {
            return null;
        }
        for (OutputItem item : response.getOutputs()) {
            if (item == null) {
                continue;
            }
            if (item.getType() == OutputType.JSON && item.getJson() != null && !item.getJson().isEmpty()) {
                try {
                    return objectMapper.writeValueAsString(item.getJson());
                } catch (Exception ignored) {
                    // fallback to text extraction below
                }
            }
            if (StringUtils.hasText(item.getText())) {
                return item.getText();
            }
        }
        return null;
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

    private void normalizeResponse(IntentAnalyzeResponse response, AiChatQueryCommand command) {
        if (response == null) {
            return;
        }
        if (!StringUtils.hasText(response.getIntentType())) {
            response.setIntentType("QUERY_RENDER");
        }
        String normalizedIntentType = response.getIntentType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!"SIMPLE_CHAT".equals(normalizedIntentType) && !"QUERY_RENDER".equals(normalizedIntentType)) {
            normalizedIntentType = "QUERY_RENDER";
        }
        response.setIntentType(normalizedIntentType);
        if (!StringUtils.hasText(response.getRewrittenQuery())) {
            response.setRewrittenQuery(command == null ? "" : command.getMessage());
        }
        if (!StringUtils.hasText(response.getSummary())) {
            response.setSummary(response.getRewrittenQuery());
        }
        if (!StringUtils.hasText(response.getSessionTitle())) {
            response.setSessionTitle(buildSessionTitle(response.getSummary(), response.getRewrittenQuery()));
        }
        if (response.getTypoCorrected() == null) {
            response.setTypoCorrected(Boolean.FALSE);
        }
        if (response.getCorrections() == null) {
            response.setCorrections(new ArrayList<>());
        }
        if (response.getTypoCorrected() == null || !response.getTypoCorrected()) {
            if (!CollectionUtils.isEmpty(response.getCorrections())) {
                response.setTypoCorrected(Boolean.TRUE);
            }
        }
        if (response.getRisk() == null) {
            response.setRisk(new IntentAnalyzeResponse.RiskInfo());
        }
        normalizeRisk(response.getRisk(), response);
        if (response.getScore() == null) {
            response.setScore(Boolean.TRUE.equals(response.getClarificationNeeded()) ? 0.65D : 0.85D);
        }
        if (response.getInvalidIntents() == null) {
            response.setInvalidIntents(new ArrayList<>());
        }
        normalizeInvalidIntents(response);
        if (!StringUtils.hasText(response.getInvalidIntentSummary())) {
            response.setInvalidIntentSummary(response.getInvalidIntents().isEmpty()
                    ? ""
                    : "识别到 " + response.getInvalidIntents().size() + " 项历史意图已失效");
        }
        if (response.getClarificationNeeded() == null) {
            response.setClarificationNeeded(Boolean.FALSE);
        }
        if (!StringUtils.hasText(response.getClarificationQuestion())) {
            response.setClarificationQuestion("");
        }
    }

    private String buildSessionTitle(String summary, String rewrittenQuery) {
        String candidate = StringUtils.hasText(summary) ? summary.trim() : rewrittenQuery;
        if (!StringUtils.hasText(candidate)) {
            return "";
        }
        return candidate.length() > 20 ? candidate.substring(0, 20) : candidate;
    }

    private void normalizeRisk(IntentAnalyzeResponse.RiskInfo risk, IntentAnalyzeResponse response) {
        if (!StringUtils.hasText(risk.getLevel())) {
            risk.setLevel(Boolean.TRUE.equals(response.getClarificationNeeded()) ? "MEDIUM" : "LOW");
        } else {
            risk.setLevel(risk.getLevel().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (risk.getItems() == null) {
            risk.setItems(new ArrayList<>());
        }
        for (IntentAnalyzeResponse.RiskItem item : risk.getItems()) {
            if (item == null) {
                continue;
            }
            if (!StringUtils.hasText(item.getType())) {
                item.setType("GENERAL");
            } else {
                item.setType(item.getType().trim().toUpperCase(java.util.Locale.ROOT));
            }
            if (!StringUtils.hasText(item.getDescription())) {
                item.setDescription("");
            }
            if (!StringUtils.hasText(item.getEvidence())) {
                item.setEvidence("");
            }
            if (item.getScore() == null) {
                item.setScore(0.60D);
            }
        }
        if (!StringUtils.hasText(risk.getSummary())) {
            risk.setSummary(risk.getItems().isEmpty()
                    ? "整体风险较低，可继续后续流程。"
                    : risk.getItems().get(0).getDescription());
        }
    }

    private void normalizeInvalidIntents(IntentAnalyzeResponse response) {
        List<IntentAnalyzeResponse.InvalidIntentItem> normalizedItems = new ArrayList<>();
        for (IntentAnalyzeResponse.InvalidIntentItem item : response.getInvalidIntents()) {
            if (item == null) {
                continue;
            }
            if (!StringUtils.hasText(item.getContent())
                    && !StringUtils.hasText(item.getReason())
                    && !StringUtils.hasText(item.getEvidence())) {
                continue;
            }
            if (!StringUtils.hasText(item.getContent())) {
                item.setContent("");
            }
            if (!StringUtils.hasText(item.getReason())) {
                item.setReason("");
            }
            if (!StringUtils.hasText(item.getEvidence())) {
                item.setEvidence("");
            }
            if (item.getScore() == null) {
                item.setScore(0.70D);
            }
            normalizedItems.add(item);
        }
        response.setInvalidIntents(normalizedItems);
    }
}
