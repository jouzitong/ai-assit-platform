package ai.platform.aiassit.conversation.workflow.evaluate.service;

import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.workflow.constants.ConversationRuntimeContextKeys;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.conversation.workflow.dto.WorkflowEvaluationResponse;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.service.ai.api.dto.ChatMessage;
import ai.platform.aiassit.service.ai.api.dto.ChatOptions;
import ai.platform.aiassit.service.ai.api.dto.ChatRequest;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.dto.OutputItem;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.dto.ResponseFormat;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.enums.MessageRole;
import ai.platform.aiassit.service.ai.api.enums.OutputType;
import ai.platform.aiassit.service.ai.api.enums.ResponseFormatType;
import ai.platform.aiassit.execution.service.AiExecutionDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.arthena.framework.common.exception.BizException;
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
 * 工作流结果评估服务。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Component
public class WorkflowResultEvaluateService {

    private static final String DEFAULT_SCENE = "ai-chat-result-evaluate";

    private static final String RESULT_EVALUATE_PROMPT = """
            你是 AI workflow 的结果评估节点。
            你的任务是判断当前查询规划与 SQL 预生成结果，是否已经足够进入渲染阶段回答用户问题。

            你必须重点判断：
            1. 当前规划是否真正理解了用户问题
            2. 当前 SQL 草案是否覆盖了主体、条件、时间范围、统计口径
            3. 当前结果如果直接进入渲染，会不会答非所问
            4. 如果不满足，应该回 query-planning 还是 sql-pre-generate

            你必须只输出严格合法 JSON：
            {
              "passed": true,
              "retryNodeCode": "",
              "reasonCode": "OK",
              "reason": "当前结果已经足够进入渲染阶段",
              "confidence": 0.92,
              "clarificationNeeded": false,
              "clarificationQuestion": "",
              "missingCapabilities": ["缺少时间范围约束"],
              "importantInfos": ["当前 SQL 仍然是伪 SQL，需要在渲染中显式说明假设"]
            }

            规则：
            1. passed 为 true 时，retryNodeCode 必须为空字符串
            2. passed 为 false 时，retryNodeCode 仅允许 query-planning 或 sql-pre-generate
            3. 如果问题属于意图理解偏差、主体识别错误、条件理解错误、时间范围错误，优先返回 query-planning
            4. 如果规划基本正确，但 SQL 草案覆盖不完整、知识命中不足、表结构推断不稳，优先返回 sql-pre-generate
            5. clarificationNeeded 为 true 时，也必须同时给出 reason 和 clarificationQuestion
            6. 不要输出 JSON 之外的任何解释
            """;

    private final AiExecutionDomainService aiExecutionDomainService;
    private final ObjectMapper objectMapper;

    public WorkflowResultEvaluateService(AiExecutionDomainService aiExecutionDomainService,
                                         ObjectMapper objectMapper) {
        this.aiExecutionDomainService = aiExecutionDomainService;
        this.objectMapper = objectMapper;
    }

    public WorkflowEvaluationResponse evaluate(ConversationRuntimeContext context) {
        ConversationQueryCommand command = context == null ? null : context.getCommand();
        if (command == null || !StringUtils.hasText(command.getMessage())) {
            return null;
        }
        ChatRequest request = buildRequest(context);
        ChatResponse result = aiExecutionDomainService.chat(request);
        if (result == null) {
            throw BizException.of(AiChatBizCodeConstant.WORKFLOW_EXECUTION_FAILED, "result evaluate failed");
        }
        WorkflowEvaluationResponse response = parseResponse(result);
        response.setRequestId(result.getRequestId());
        response.setModel(result.getModel());
        normalizeResponse(response);
        return response;
    }

    private ChatRequest buildRequest(ConversationRuntimeContext context) {
        ConversationQueryCommand command = context.getCommand();
        ChatRequest request = new ChatRequest();
        request.setClientType(null);
        request.setModelCode(command.getApiModel());
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
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() + "-result-evaluate" : DEFAULT_SCENE);
        request.setMeta(meta);
        return request;
    }

    private List<ChatMessage> buildMessages(ConversationRuntimeContext context) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(buildMessage(MessageRole.SYSTEM, RESULT_EVALUATE_PROMPT));
        String contextText = buildContextText(context);
        if (StringUtils.hasText(contextText)) {
            messages.add(buildMessage(MessageRole.SYSTEM, contextText));
        }
        for (AiChatMessageDTO historyMessage : resolveHistoryMessages(context)) {
            ChatMessage message = toChatMessage(historyMessage);
            if (message != null) {
                messages.add(message);
            }
        }
        messages.add(buildMessage(MessageRole.USER, buildEvaluateInput(context)));
        return messages;
    }

    private String buildContextText(ConversationRuntimeContext context) {
        StringBuilder builder = new StringBuilder();
        Object intentAnalyzeResponse = context.get(ConversationRuntimeContextKeys.Planning.INTENT_ANALYZE_RESPONSE);
        if (intentAnalyzeResponse != null) {
            builder.append("基础意图分析：").append(intentAnalyzeResponse).append('\n');
        }
        if (StringUtils.hasText(context.getOrCreateUserMessageContext().getSummary())) {
            builder.append("用户消息上下文汇总：").append('\n')
                    .append(context.getOrCreateUserMessageContext().getSummary()).append('\n');
        }
        return builder.toString().trim();
    }

    private List<AiChatMessageDTO> resolveHistoryMessages(ConversationRuntimeContext context) {
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

    private String buildEvaluateInput(ConversationRuntimeContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userQuery", context.getCommand() == null ? null : context.getCommand().getMessage());
        payload.put("analysisResult", context.getAnalysisResult());
        payload.put("planningResult", context.get(ConversationRuntimeContextKeys.Planning.QUERY_PLAN_RESULT));
        payload.put("knowledgeResult", context.getKnowledgeResult());
        payload.put("knowledgeSearchResponse", context.get(ConversationRuntimeContextKeys.Capability.KNOWLEDGE_SEARCH_RESPONSE));
        payload.put("sqlPreGenerateResult", context.getSqlPreGenerateResult());
        payload.put("generatedSql", context.getGeneratedSql());
        payload.put("renderRequirements", List.of(
                "必须判断当前结果是否已经足够回答用户问题",
                "如果仍有明显语义缺口，不允许直接放行到 render",
                "如果规划偏了就回 query-planning，如果 SQL 草案不够就回 sql-pre-generate"
        ));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BizException(ex);
        }
    }

    private ResponseFormat buildResponseFormat() {
        ResponseFormat responseFormat = new ResponseFormat();
        responseFormat.setType(ResponseFormatType.JSON_SCHEMA);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("passed", Map.of("type", "boolean"));
        properties.put("retryNodeCode", Map.of("type", "string"));
        properties.put("reasonCode", Map.of("type", "string"));
        properties.put("reason", Map.of("type", "string"));
        properties.put("confidence", Map.of("type", "number"));
        properties.put("clarificationNeeded", Map.of("type", "boolean"));
        properties.put("clarificationQuestion", Map.of("type", "string"));
        properties.put("missingCapabilities", buildStringArraySchema());
        properties.put("importantInfos", buildStringArraySchema());
        schema.put("properties", properties);
        schema.put("required", List.of(
                "passed",
                "retryNodeCode",
                "reasonCode",
                "reason",
                "confidence",
                "clarificationNeeded",
                "clarificationQuestion",
                "missingCapabilities",
                "importantInfos"
        ));
        responseFormat.setSchema(schema);
        return responseFormat;
    }

    private Map<String, Object> buildStringArraySchema() {
        return Map.of("type", "array", "items", Map.of("type", "string"));
    }

    private WorkflowEvaluationResponse parseResponse(ChatResponse response) {
        String rawOutput = extractOutput(response);
        if (!StringUtils.hasText(rawOutput)) {
            WorkflowEvaluationResponse fallback = new WorkflowEvaluationResponse();
            fallback.setRawOutput(null);
            return fallback;
        }
        try {
            WorkflowEvaluationResponse result = objectMapper.readValue(cleanJson(rawOutput), WorkflowEvaluationResponse.class);
            result.setRawOutput(rawOutput);
            return result;
        } catch (Exception ex) {
            WorkflowEvaluationResponse fallback = new WorkflowEvaluationResponse();
            fallback.setRawOutput(rawOutput);
            fallback.setReason(rawOutput.trim());
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

    private void normalizeResponse(WorkflowEvaluationResponse response) {
        if (response == null) {
            return;
        }
        if (response.getPassed() == null) {
            response.setPassed(Boolean.TRUE);
        }
        if (!StringUtils.hasText(response.getReasonCode())) {
            response.setReasonCode(response.getPassed() ? "OK" : "RETRY_REQUIRED");
        }
        if (!StringUtils.hasText(response.getReason())) {
            response.setReason(response.getPassed() ? "evaluation passed" : "evaluation requires retry");
        }
        if (response.getConfidence() == null) {
            response.setConfidence(response.getPassed() ? 0.80D : 0.60D);
        }
        if (response.getClarificationNeeded() == null) {
            response.setClarificationNeeded(Boolean.FALSE);
        }
        if (response.getMissingCapabilities() == null) {
            response.setMissingCapabilities(new ArrayList<>());
        }
        if (response.getImportantInfos() == null) {
            response.setImportantInfos(new ArrayList<>());
        }
        if (response.getPassed()) {
            response.setRetryNodeCode("");
            return;
        }
        String retryNodeCode = response.getRetryNodeCode();
        if (!WorkflowNodeCodes.QUERY_PLANNING.getNodeCode().equals(retryNodeCode)
                && !WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode().equals(retryNodeCode)) {
            response.setRetryNodeCode(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode());
        }
    }
}
