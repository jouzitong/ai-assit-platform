package ai.platform.aiassit.chat.core.workflow.planning.service;

import ai.platform.aiassist.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassist.service.ai.api.dto.ChatMessage;
import ai.platform.aiassist.service.ai.api.dto.ChatOptions;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.IntentAnalyzeResponse;
import ai.platform.aiassist.service.ai.api.dto.OutputItem;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassist.service.ai.api.dto.ResponseFormat;
import ai.platform.aiassist.service.ai.api.enums.MessageRole;
import ai.platform.aiassist.service.ai.api.enums.OutputType;
import ai.platform.aiassist.service.ai.api.enums.ResponseFormatType;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.athena.framework.web.vo.R;
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

            你必须只返回严格合法的 JSON，对象结构固定如下：
            {
              "businessType": "预留字段，当前阶段暂不使用；如果提供，仅作为候选业务域，后续真正使用时必须重新核对",
              "intentType": "意图类型，仅允许 SIMPLE_CHAT 或 QUERY_RENDER",
              "rewrittenQuery": "归一化后的用户问题",
              "summary": "用户需求摘要，突出真正想解决的问题",
              "intentLabels": ["标签1"],
              "metrics": ["指标1"],
              "dimensions": ["维度1"],
              "candidateDatasets": ["候选数据集1"],
              "requiredContext": ["仍需补充的上下文1"],
              "risks": ["需要注意的风险1"],
              "clarificationNeeded": false,
              "clarificationQuestions": ["建议追问1"],
              "importantInfos": ["其他重要信息1"],
              "timeRange": {"granularity":"DAY","startDate":"2026-06-01","endDate":"2026-06-15"}
            }

            字段要求：
            1. businessType、intentType、rewrittenQuery、summary 必须为非空字符串
            2. 所有数组字段必须返回 JSON 数组，可为空数组
            3. clarificationNeeded 必须返回布尔值
            4. timeRange 必须返回 JSON 对象，可为空对象
            5. importantInfos 重点补充业务口径、隐含对象、默认前提、限制条件等
            6. businessType 当前只是预留候选字段，不作为最终业务判断依据；如果无法稳定判断，请返回 GENERAL
            7. intentType 取值规则：
               - SIMPLE_CHAT：普通闲聊、解释、问答、建议，不需要进入查数渲染链路
               - QUERY_RENDER：用户核心目标是查数据、分析数据、生成报表、做指标解释或需要进入查询渲染链路
            8. 如果无法绝对确认，但问题明显偏向数据查询/分析，优先返回 QUERY_RENDER
            9. 不要输出 JSON 之外的任何解释
            """;

    private final AiChatExecutionApi aiChatExecutionApi;
    private final ObjectMapper objectMapper;

    public WorkflowIntentAnalyzeService(AiChatExecutionApi aiChatExecutionApi,
                                        ObjectMapper objectMapper) {
        this.aiChatExecutionApi = aiChatExecutionApi;
        this.objectMapper = objectMapper;
    }

    public IntentAnalyzeResponse analyze(WorkflowContext context) {
        AiChatQueryCommand command = context == null ? null : context.getCommand();
        if (command == null || !StringUtils.hasText(command.getMessage())) {
            return null;
        }
        ChatRequest request = buildRequest(context);
        R<ChatResponse> result = aiChatExecutionApi.chat(request);
        if (result == null || result.getCode() != 0 || result.getData() == null) {
            throw new IllegalStateException("base intent analyze failed");
        }
        IntentAnalyzeResponse response = parseResponse(result.getData(), command);
        response.setRequestId(result.getData().getRequestId());
        response.setModel(result.getData().getModel());
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
        properties.put("businessType", Map.of("type", "string"));
        properties.put("intentType", Map.of("type", "string"));
        properties.put("rewrittenQuery", Map.of("type", "string"));
        properties.put("summary", Map.of("type", "string"));
        properties.put("intentLabels", buildStringArraySchema());
        properties.put("metrics", buildStringArraySchema());
        properties.put("dimensions", buildStringArraySchema());
        properties.put("candidateDatasets", buildStringArraySchema());
        properties.put("requiredContext", buildStringArraySchema());
        properties.put("risks", buildStringArraySchema());
        properties.put("clarificationNeeded", Map.of("type", "boolean"));
        properties.put("clarificationQuestions", buildStringArraySchema());
        properties.put("importantInfos", buildStringArraySchema());
        properties.put("timeRange", Map.of("type", "object", "additionalProperties", true));
        schema.put("properties", properties);
        schema.put("required", List.of(
                "businessType",
                "intentType",
                "rewrittenQuery",
                "summary",
                "intentLabels",
                "metrics",
                "dimensions",
                "candidateDatasets",
                "requiredContext",
                "risks",
                "clarificationNeeded",
                "clarificationQuestions",
                "importantInfos",
                "timeRange"
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

    private IntentAnalyzeResponse parseResponse(ChatResponse response, AiChatQueryCommand command) {
        String rawOutput = extractOutput(response);
        if (!StringUtils.hasText(rawOutput)) {
            IntentAnalyzeResponse fallback = new IntentAnalyzeResponse();
            fallback.setRawOutput(null);
            fallback.setRewrittenQuery(command.getMessage());
            fallback.setSummary(command.getMessage());
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
        if (!StringUtils.hasText(response.getBusinessType())) {
            response.setBusinessType("GENERAL");
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
        if (response.getIntentLabels() == null) {
            response.setIntentLabels(new ArrayList<>());
        }
        if (response.getMetrics() == null) {
            response.setMetrics(new ArrayList<>());
        }
        if (response.getDimensions() == null) {
            response.setDimensions(new ArrayList<>());
        }
        if (response.getCandidateDatasets() == null) {
            response.setCandidateDatasets(new ArrayList<>());
        }
        if (response.getRequiredContext() == null) {
            response.setRequiredContext(new ArrayList<>());
        }
        if (response.getRisks() == null) {
            response.setRisks(new ArrayList<>());
        }
        if (response.getClarificationQuestions() == null) {
            response.setClarificationQuestions(new ArrayList<>());
        }
        if (response.getImportantInfos() == null) {
            response.setImportantInfos(new ArrayList<>());
        }
        if (response.getTimeRange() == null) {
            response.setTimeRange(new LinkedHashMap<>());
        }
        if (response.getClarificationNeeded() == null) {
            response.setClarificationNeeded(Boolean.FALSE);
        }
    }
}
