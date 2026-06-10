package ai.platform.aiassit.chat.core.workflow.node.impl;

import ai.platform.aiassist.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassist.service.ai.api.AiMetaQueryApi;
import ai.platform.aiassist.service.ai.api.dto.AiMetaQueryRequest;
import ai.platform.aiassist.service.ai.api.dto.AiModelConfigDTO;
import ai.platform.aiassist.service.ai.api.dto.ChatMessage;
import ai.platform.aiassist.service.ai.api.dto.ChatOptions;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.OutputItem;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassist.service.ai.api.enums.MessageRole;
import ai.platform.aiassist.service.ai.api.enums.ProviderType;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.config.WorkflowProperties;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import ai.platform.aiassit.chat.core.workflow.support.WorkflowHistoryRecorder;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactStage;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * 查询规划节点，负责抽取当前问题的执行意图并创建本轮执行上下文。
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Service
@Slf4j
public class QueryPlanningNode extends BaseWorkflowNode {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DEFAULT_SCENE = "ai-chat-query-planning";
    private static final String PLANNING_PROMPT = """
            你是一个智能问数工作流的查询规划节点。
            你需要根据用户当前问题和已有对话历史，输出严格合法的 JSON。

            你必须只输出以下 JSON 结构，不允许输出 markdown、解释、代码块：
            {
              "sessionTitle": "新会话标题，只有新会话首轮时必填，其他情况可返回空字符串",
              "userGoal": "用户核心目标",
              "analysisSummary": "给后续节点使用的简明分析摘要",
              "analysisDimensions": ["关键分析维度1", "关键分析维度2"],
              "requiredContext": ["需要的知识上下文或数据口径1"],
              "sqlFocus": ["SQL 生成重点1"],
              "risks": ["风险点1"],
              "needClarification": false
            }

            字段要求：
            1. userGoal、analysisSummary 必须为非空中文字符串
            2. analysisDimensions、requiredContext、sqlFocus、risks 必须为 JSON 数组，可为空数组
            3. needClarification 必须为布尔值
            4. 如果是新会话首轮，sessionTitle 必须给出 6 到 20 个字的简洁标题；否则返回空字符串
            5. 不要输出任何额外字段
            """;

    private final AiChatExecutionApi aiChatExecutionApi;
    private final AiMetaQueryApi aiMetaQueryApi;
    private final AiChatRoundService roundService;
    private final AiChatSessionService sessionService;
    private final WorkflowHistoryRecorder historyRecorder;
    private final ObjectMapper objectMapper;
    private final WorkflowProperties workflowProperties;

    public QueryPlanningNode(AiChatExecutionApi aiChatExecutionApi,
                             AiMetaQueryApi aiMetaQueryApi,
                             AiChatRoundService roundService,
                             AiChatSessionService sessionService,
                             WorkflowHistoryRecorder historyRecorder,
                             ObjectMapper objectMapper,
                             WorkflowProperties workflowProperties) {
        this.aiChatExecutionApi = aiChatExecutionApi;
        this.aiMetaQueryApi = aiMetaQueryApi;
        this.roundService = roundService;
        this.sessionService = sessionService;
        this.historyRecorder = historyRecorder;
        this.objectMapper = objectMapper;
        this.workflowProperties = workflowProperties;
    }

    @Override
    protected NodeResult doExecute(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        if (command == null) {
            return NodeResult.fail("command is required");
        }
        if (context.getSession() == null) {
            return NodeResult.fail("session is required");
        }
        if (context.getRound() == null) {
            return NodeResult.fail("round is required");
        }
        if (context.getCurrentUserMessage() == null) {
            return NodeResult.fail("currentUserMessage is required");
        }
        if (!StringUtils.hasText(command.getMessage())) {
            return NodeResult.fail("message is required");
        }

        try {
            List<AiChatMessageDTO> historyMessages = new ArrayList<>(context.getSessionMessages());
            historyMessages = historyMessages.stream()
                    .filter(message -> context.getCurrentUserMessage() == null
                            || !Objects.equals(message.getMessageCode(), context.getCurrentUserMessage().getMessageCode()))
                    .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                    .toList();

            ChatRequest planningRequest = buildPlanningRequest(command, context, historyMessages);
            ChatResponse planningResponse = aiChatExecutionApi.chat(planningRequest);
            PlanningResult planningResult = parsePlanningResult(
                    extractAnswer(planningResponse),
                    historyMessages.isEmpty()
            );
            if (historyMessages.isEmpty()) {
                refreshSessionName(context, planningResult);
            }
            String analysisResult = buildAnalysisSummary(planningResult);

            context.setEngineRequest(planningRequest);
            context.setEngineResponse(planningResponse);
            context.setAnalysisResult(analysisResult);
            context.put("queryPlan", analysisResult);
            context.put("queryPlanResult", planningResult);
            context.put("planningRequestId", planningResponse == null ? null : planningResponse.getRequestId());
            context.publishEvent("query-plan-ready", "query plan prepared");
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.QUERY_PLAN.name(),
                    AiChatArtifactStage.PLAN.name(),
                    "查询规划",
                    planningResult,
                    AiChatContentFormat.JSON.name(),
                    true,
                    STATUS_SUCCESS,
                    context.getCurrentUserMessage().getMessageCode(),
                    planningResponse == null ? null : planningResponse.getRequestId()
            );

            return NodeResult.success(null);
        } catch (Exception ex) {
            log.error("query planning failed, sessionCode={}", context.getSession().getSessionCode(), ex);
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.WORKFLOW_ERROR.name(),
                    AiChatArtifactStage.PLAN.name(),
                    "查询规划失败",
                    ex.getMessage(),
                    AiChatContentFormat.PLAIN_TEXT.name(),
                    true,
                    STATUS_FAILED,
                    context.getCurrentUserMessage() == null ? null : context.getCurrentUserMessage().getMessageCode(),
                    null
            );
            finishRound(context.getRound(), context.getEngineRequest(), null, STATUS_FAILED);
            return NodeResult.fail(ex.getMessage());
        }
    }

    @Override
    public String type() {
        return "Query-Planning";
    }

    @Override
    public int order() {
        return 200;
    }

    private ChatRequest buildPlanningRequest(AiChatQueryCommand command,
                                             WorkflowContext context,
                                             List<AiChatMessageDTO> historyMessages) {
        ChatRequest request = new ChatRequest();
        request.setProvider(resolveProviderType(command));
        request.setModel(resolveActualModel(command.getApiModel()));

        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage systemMessage = new ChatMessage();
        systemMessage.setRole(MessageRole.SYSTEM);
        systemMessage.setContent(PLANNING_PROMPT);
        messages.add(systemMessage);
        String planningContext = buildPlanningContext(context);
        if (StringUtils.hasText(planningContext)) {
            ChatMessage contextMessage = new ChatMessage();
            contextMessage.setRole(MessageRole.SYSTEM);
            contextMessage.setContent(planningContext);
            messages.add(contextMessage);
        }

        if (!CollectionUtils.isEmpty(historyMessages)) {
            for (AiChatMessageDTO historyMessage : historyMessages) {
                ChatMessage message = new ChatMessage();
                message.setRole(resolveMessageRole(historyMessage.getRole()));
                message.setContent(historyMessage.getContent());
                messages.add(message);
            }
        }

        ChatMessage currentUserMessage = new ChatMessage();
        currentUserMessage.setRole(MessageRole.USER);
        currentUserMessage.setContent(context.getCurrentUserMessage().getContent());
        messages.add(currentUserMessage);
        request.setMessages(messages);

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(resolveMaxTokens(command.getApiModel()));
        options.setTimeoutMs(30_000);
        request.setOptions(options);

        RequestMeta meta = new RequestMeta();
        meta.setTraceId(StringUtils.hasText(command.getTraceId()) ? command.getTraceId() : generateCode("trace"));
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() : DEFAULT_SCENE);
        request.setMeta(meta);
        return request;
    }

    private String buildPlanningContext(WorkflowContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("是否新会话首轮：")
                .append(context.getCurrentUserMessage() != null && Integer.valueOf(1).equals(context.getCurrentUserMessage().getSortNo()))
                .append('\n');
        List<String> resolvedTerms = context.get("resolvedBusinessTerms");
        if (!CollectionUtils.isEmpty(resolvedTerms)) {
            builder.append("业务术语补充：").append(resolvedTerms).append('\n');
        }
        Object normalizedTimeRange = context.get("normalizedTimeRange");
        if (normalizedTimeRange != null) {
            builder.append("时间范围补充：").append(normalizedTimeRange).append('\n');
        }
        return builder.toString().trim();
    }

    private PlanningResult parsePlanningResult(String rawText, boolean requireSessionTitle) {
        String currentText = rawText;
        String validationError = null;
        int maxRetry = resolvePlanningStructureMaxRetry();
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                PlanningResult result = objectMapper.readValue(cleanJson(currentText), PlanningResult.class);
                validatePlanningResult(result, requireSessionTitle);
                return normalizePlanningResult(result);
            } catch (Exception ex) {
                validationError = ex.getMessage();
                if (attempt == maxRetry) {
                    throw new IllegalArgumentException("planning result parse failed: " + validationError, ex);
                }
                currentText = retryPlanningWithFeedback(currentText, validationError, requireSessionTitle);
            }
        }
        throw new IllegalArgumentException("planning result parse failed: " + validationError);
    }

    private int resolvePlanningStructureMaxRetry() {
        Integer configured = workflowProperties == null ? null : workflowProperties.getPlanningStructureMaxRetry();
        if (configured == null || configured < 0) {
            return 5;
        }
        return configured;
    }

    private String retryPlanningWithFeedback(String previousOutput,
                                            String validationError,
                                            boolean requireSessionTitle) {
        ChatRequest retryRequest = new ChatRequest();
        retryRequest.setProvider(ProviderType.DASHSCOPE);
        retryRequest.setModel(resolveActualModel(null));

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(buildMessage(MessageRole.SYSTEM, PLANNING_PROMPT));
        messages.add(buildMessage(MessageRole.ASSISTANT, defaultText(previousOutput)));
        messages.add(buildMessage(MessageRole.USER, """
                你上一次返回的 JSON 不合法，请严格修正后重新返回。
                校验错误：
                %s
                额外要求：
                - 只返回 JSON
                - 不允许代码块
                - sessionTitle%s
                """.formatted(validationError, requireSessionTitle ? "必须非空" : "可为空字符串")));
        retryRequest.setMessages(messages);

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(1024);
        options.setTimeoutMs(30_000);
        retryRequest.setOptions(options);

        ChatResponse retryResponse = aiChatExecutionApi.chat(retryRequest);
        return extractAnswer(retryResponse);
    }

    private void validatePlanningResult(PlanningResult result, boolean requireSessionTitle) {
        if (result == null) {
            throw new IllegalArgumentException("result is null");
        }
        if (!StringUtils.hasText(result.getUserGoal())) {
            throw new IllegalArgumentException("userGoal is required");
        }
        if (!StringUtils.hasText(result.getAnalysisSummary())) {
            throw new IllegalArgumentException("analysisSummary is required");
        }
        if (result.getAnalysisDimensions() == null) {
            throw new IllegalArgumentException("analysisDimensions is required");
        }
        if (result.getRequiredContext() == null) {
            throw new IllegalArgumentException("requiredContext is required");
        }
        if (result.getSqlFocus() == null) {
            throw new IllegalArgumentException("sqlFocus is required");
        }
        if (result.getRisks() == null) {
            throw new IllegalArgumentException("risks is required");
        }
        if (result.getNeedClarification() == null) {
            throw new IllegalArgumentException("needClarification is required");
        }
        if (requireSessionTitle && !StringUtils.hasText(result.getSessionTitle())) {
            throw new IllegalArgumentException("sessionTitle is required for new conversation");
        }
    }

    private PlanningResult normalizePlanningResult(PlanningResult result) {
        result.setSessionTitle(trimToNull(result.getSessionTitle()));
        result.setUserGoal(result.getUserGoal().trim());
        result.setAnalysisSummary(result.getAnalysisSummary().trim());
        result.setAnalysisDimensions(normalizeList(result.getAnalysisDimensions()));
        result.setRequiredContext(normalizeList(result.getRequiredContext()));
        result.setSqlFocus(normalizeList(result.getSqlFocus()));
        result.setRisks(normalizeList(result.getRisks()));
        return result;
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private String buildAnalysisSummary(PlanningResult result) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("用户目标：" + result.getUserGoal());
        joiner.add("分析摘要：" + result.getAnalysisSummary());
        if (!CollectionUtils.isEmpty(result.getAnalysisDimensions())) {
            joiner.add("关键维度：" + String.join("；", result.getAnalysisDimensions()));
        }
        if (!CollectionUtils.isEmpty(result.getRequiredContext())) {
            joiner.add("所需上下文：" + String.join("；", result.getRequiredContext()));
        }
        if (!CollectionUtils.isEmpty(result.getSqlFocus())) {
            joiner.add("SQL 重点：" + String.join("；", result.getSqlFocus()));
        }
        if (!CollectionUtils.isEmpty(result.getRisks())) {
            joiner.add("风险点：" + String.join("；", result.getRisks()));
        }
        joiner.add("是否需要澄清：" + Boolean.TRUE.equals(result.getNeedClarification()));
        return joiner.toString();
    }

    private void refreshSessionName(WorkflowContext context, PlanningResult result) {
        if (context.getSession() == null || context.getSession().getId() == null || !StringUtils.hasText(result.getSessionTitle())) {
            return;
        }
        AiChatSessionDTO update = new AiChatSessionDTO();
        update.setSessionName(result.getSessionTitle().trim());
        AiChatSessionDTO updated = sessionService.edit(context.getSession().getId(), update);
        if (updated != null) {
            context.setSession(updated);
        } else {
            context.getSession().setSessionName(result.getSessionTitle().trim());
        }
    }

    private ChatMessage buildMessage(MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private String cleanJson(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("planning output is empty");
        }
        String cleaned = text.trim();
        cleaned = cleaned.replace("```json", "");
        cleaned = cleaned.replace("```JSON", "");
        cleaned = cleaned.replace("```", "");
        return cleaned.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private void finishRound(AiChatRoundDTO round, ChatRequest request, String actualModel, String status) {
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

    private ProviderType resolveProviderType(AiChatQueryCommand command) {
        AiModelConfigDTO config = findModelConfigByApiModel(command == null ? null : command.getApiModel());
        if (config != null && StringUtils.hasText(config.getProviderCode())) {
            return resolveProviderType(config.getProviderCode());
        }
        return ProviderType.DASHSCOPE;
    }

    private ProviderType resolveProviderType(String providerCode) {
        if (!StringUtils.hasText(providerCode)) {
            return ProviderType.DASHSCOPE;
        }
        try {
            return ProviderType.valueOf(providerCode.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return ProviderType.DASHSCOPE;
        }
    }

    private AiModelConfigDTO findModelConfigByApiModel(String apiModel) {
        AiMetaQueryRequest request = new AiMetaQueryRequest();
        request.setEnabled(Boolean.TRUE);
        return aiMetaQueryApi.listModels(request).stream()
                .filter(Objects::nonNull)
                .filter(config -> StringUtils.hasText(config.getApiModel()))
                .filter(config -> !StringUtils.hasText(apiModel) || apiModel.trim().equals(config.getApiModel().trim()))
                .findFirst()
                .orElse(null);
    }

    private int resolveMaxTokens(String apiModel) {
        AiModelConfigDTO config = findModelConfigByApiModel(apiModel);
        return config == null || config.getMaxOutputTokens() == null ? 1024 : config.getMaxOutputTokens();
    }

    private String resolveActualModel(String apiModel) {
        if (StringUtils.hasText(apiModel)) {
            return apiModel.trim();
        }
        AiModelConfigDTO config = findModelConfigByApiModel(null);
        if (config != null && StringUtils.hasText(config.getApiModel())) {
            return config.getApiModel().trim();
        }
        if (config != null && StringUtils.hasText(config.getModelCode())) {
            return config.getModelCode().trim();
        }
        return "qwen-math-turbo";
    }

    private MessageRole resolveMessageRole(String role) {
        if (!StringUtils.hasText(role)) {
            return MessageRole.USER;
        }
        try {
            return MessageRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return MessageRole.USER;
        }
    }

    private String generateCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    public static class PlanningResult {
        private String sessionTitle;
        private String userGoal;
        private String analysisSummary;
        private List<String> analysisDimensions = new ArrayList<>();
        private List<String> requiredContext = new ArrayList<>();
        private List<String> sqlFocus = new ArrayList<>();
        private List<String> risks = new ArrayList<>();
        private Boolean needClarification;

        public String getSessionTitle() {
            return sessionTitle;
        }

        public void setSessionTitle(String sessionTitle) {
            this.sessionTitle = sessionTitle;
        }

        public String getUserGoal() {
            return userGoal;
        }

        public void setUserGoal(String userGoal) {
            this.userGoal = userGoal;
        }

        public String getAnalysisSummary() {
            return analysisSummary;
        }

        public void setAnalysisSummary(String analysisSummary) {
            this.analysisSummary = analysisSummary;
        }

        public List<String> getAnalysisDimensions() {
            return analysisDimensions;
        }

        public void setAnalysisDimensions(List<String> analysisDimensions) {
            this.analysisDimensions = analysisDimensions;
        }

        public List<String> getRequiredContext() {
            return requiredContext;
        }

        public void setRequiredContext(List<String> requiredContext) {
            this.requiredContext = requiredContext;
        }

        public List<String> getSqlFocus() {
            return sqlFocus;
        }

        public void setSqlFocus(List<String> sqlFocus) {
            this.sqlFocus = sqlFocus;
        }

        public List<String> getRisks() {
            return risks;
        }

        public void setRisks(List<String> risks) {
            this.risks = risks;
        }

        public Boolean getNeedClarification() {
            return needClarification;
        }

        public void setNeedClarification(Boolean needClarification) {
            this.needClarification = needClarification;
        }
    }
}
