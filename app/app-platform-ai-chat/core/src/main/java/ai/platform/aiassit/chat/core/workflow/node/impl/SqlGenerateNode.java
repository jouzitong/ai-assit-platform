package ai.platform.aiassit.chat.core.workflow.node.impl;

import ai.platform.aiassist.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassist.service.ai.api.AiMetaQueryApi;
import ai.platform.aiassist.service.ai.api.dto.AiMetaQueryRequest;
import ai.platform.aiassist.service.ai.api.dto.AiModelConfigDTO;
import ai.platform.aiassist.service.ai.api.dto.ChatMessage;
import ai.platform.aiassist.service.ai.api.dto.ChatOptions;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchItem;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.OutputItem;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassist.service.ai.api.enums.MessageRole;
import ai.platform.aiassist.service.ai.api.enums.ProviderType;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeCapabilityConfig;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.chat.core.workflow.capability.impl.KnowledgeRetrievePromptContextCapability;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import ai.platform.aiassit.chat.core.workflow.planning.contract.PlanningResult;
import ai.platform.aiassit.chat.core.workflow.sql.contract.SqlPreGenerateResult;
import ai.platform.aiassit.chat.core.workflow.support.WorkflowHistoryRecorder;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactStage;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * SQL 生成节点，负责基于规划和知识上下文生成候选 SQL。
 *
 * <p>功能：</p>
 * <ul>
 *     <li>消费用户问题、查询规划、知识上下文和必要历史消息。</li>
 *     <li>调用模型生成候选 SQL 草案。</li>
 *     <li>对模型输出做最小清洗，并将候选 SQL 写入 {@link WorkflowContext}。</li>
 *     <li>记录 SQL 草案 artifact，供后续校验节点继续处理。</li>
 * </ul>
 *
 * <p>边界描述：</p>
 * <ul>
 *     <li>只负责生成候选 SQL，不判定最终安全性与可执行性。</li>
 *     <li>不负责知识检索，不负责真实执行，不负责最终答案渲染。</li>
 *     <li>即使携带假设说明，也只输出 SQL 文本，不扩展成解释性回答。</li>
 * </ul>
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Service
@Slf4j
public class SqlGenerateNode extends BaseWorkflowNode {

    private static final String DEFAULT_SCENE = "ai-chat-sql-generate";
    private static final String DEFAULT_KB_ID = "w05enpcxa4";
    private static final int DEFAULT_KB_TOP_K = 20;
    private static final String SQL_GENERATION_POLICY_KEY = "sqlGenerationPolicy";
    private static final String USER_PREFERENCE_KEY = "resolvedUserPreferences";
    private static final String SQL_GENERATION_PROMPT = """
            你是一个 NL2SQL 生成节点。
            请严格根据提供的用户问题、查询规划和知识上下文，生成一条候选 SQL。
            你还会收到“SQL 生成规范”和“用户偏好”两个补充部分：
            - SQL 生成规范属于硬约束，必须遵守
            - 用户偏好属于软参考，仅在不与用户本轮要求和硬约束冲突时采用

            约束要求：
            1. 优先输出单条 SELECT 或 WITH 查询
            2. 不允许输出 INSERT、UPDATE、DELETE、DROP、ALTER、TRUNCATE、CREATE、MERGE
            3. 如果上下文不足，也要尽量输出最合理的查询，并在 SQL 前用单行注释说明假设
            4. 最终输出只包含 SQL 文本，可带 SQL 注释，不要解释
            """;

    private final AiChatExecutionApi aiChatExecutionApi;
    private final AiMetaQueryApi aiMetaQueryApi;
    private final WorkflowHistoryRecorder historyRecorder;

    public SqlGenerateNode(AiChatExecutionApi aiChatExecutionApi,
                           AiMetaQueryApi aiMetaQueryApi,
                           WorkflowHistoryRecorder historyRecorder) {
        this.aiChatExecutionApi = aiChatExecutionApi;
        this.aiMetaQueryApi = aiMetaQueryApi;
        this.historyRecorder = historyRecorder;
    }

    @Override
    protected void beforeExecute(WorkflowContext context, WorkflowNodeConfig nodeConfig) {
        if (nodeConfig == null) {
            return;
        }
        List<WorkflowNodeCapabilityConfig> capabilities = nodeConfig.getCapabilities();
        if (capabilities == null) {
            capabilities = new ArrayList<>();
            nodeConfig.setCapabilities(capabilities);
        }
        WorkflowNodeCapabilityConfig knowledgeCapability = null;
        for (WorkflowNodeCapabilityConfig capability : capabilities) {
            if (capability != null && KnowledgeRetrievePromptContextCapability.CODE.equals(capability.getCode())) {
                knowledgeCapability = capability;
                break;
            }
        }
        if (knowledgeCapability == null) {
            knowledgeCapability = new WorkflowNodeCapabilityConfig();
            knowledgeCapability.setCode(KnowledgeRetrievePromptContextCapability.CODE);
            knowledgeCapability.setRequired(Boolean.FALSE);
            knowledgeCapability.setSort(100);
            capabilities.add(knowledgeCapability);
        }
        knowledgeCapability.getOptions().putIfAbsent("title", "SQL 相关知识库上下文");
        knowledgeCapability.getOptions().put("query", buildKnowledgeRetrieveQuery(context));
        knowledgeCapability.getOptions().putIfAbsent("queryTemplate", """
                用户问题：
                {message}

                查询规划：
                {analysis}
                """.trim());
        knowledgeCapability.getOptions().put("kbId", DEFAULT_KB_ID);
        knowledgeCapability.getOptions().put("topK", DEFAULT_KB_TOP_K);
    }

    private String buildKnowledgeRetrieveQuery(WorkflowContext context) {
        PlanningResult planningResult = context.get(WorkflowContextKeys.Planning.QUERY_PLAN_RESULT);
        if (planningResult == null || planningResult.getSubject() == null) {
            return null;
        }
        PlanningResult.Subject subject = planningResult.getSubject();
        Set<String> subjectTerms = new LinkedHashSet<>();
        appendTerm(subjectTerms, subject.getName());
        appendTerm(subjectTerms, subject.getValue());
        appendTerms(subjectTerms, subject.getAliases());

        List<String> relationLines = new ArrayList<>();
        if (!CollectionUtils.isEmpty(subject.getRelations())) {
            for (PlanningResult.RelationItem relation : subject.getRelations()) {
                if (relation == null) {
                    continue;
                }
                Set<String> relationTerms = new LinkedHashSet<>();
                appendTerm(relationTerms, relation.getName());
                appendTerms(relationTerms, relation.getValues());
                appendTerms(relationTerms, relation.getAliases());
                if (!relationTerms.isEmpty()) {
                    relationLines.add(String.join(" / ", relationTerms));
                }
            }
        }

        if (subjectTerms.isEmpty() && relationLines.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("请基于以下查询规划对象，检索完整的真实表信息、字段说明、表关系和 SQL 使用口径。");
        if (!subjectTerms.isEmpty()) {
            builder.append("\n主体对象：").append(String.join(" / ", subjectTerms));
        }
        if (!relationLines.isEmpty()) {
            builder.append("\n关联对象：");
            for (int i = 0; i < relationLines.size(); i++) {
                builder.append("\n").append(i + 1).append(". ").append(relationLines.get(i));
            }
        }
        if (StringUtils.hasText(context.getCommand() == null ? null : context.getCommand().getMessage())) {
            builder.append("\n用户问题：").append(context.getCommand().getMessage().trim());
        }
        if (StringUtils.hasText(context.getAnalysisResult())) {
            builder.append("\n规划摘要：").append(context.getAnalysisResult().trim());
        }
        return builder.toString().trim();
    }

    private void appendTerm(Set<String> target, String value) {
        if (StringUtils.hasText(value)) {
            target.add(value.trim());
        }
    }

    private void appendTerms(Set<String> target, List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return;
        }
        for (String value : values) {
            appendTerm(target, value);
        }
    }

    @Override
    protected NodeResult doExecute(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        if (command == null) {
            return NodeResult.fail("command is required");
        }
        if (!StringUtils.hasText(context.getAnalysisResult())) {
            return NodeResult.fail("analysisResult is required");
        }

        try {
            SqlPreGenerateResult sqlPreGenerateResult = buildSqlPreGenerateResult(context);
            context.setSqlPreGenerateResult(sqlPreGenerateResult);
            context.put(WorkflowContextKeys.SqlGenerate.PRE_GENERATE_RESULT, sqlPreGenerateResult);
            ChatRequest request = buildRequest(command, context);
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()).setRequest(request);
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()).setStatus("RUNNING");
            ChatResponse response = aiChatExecutionApi.chat(request).getData();
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()).setResponse(response);
            String generatedSql = normalizeSql(extractAnswer(response));
            if (!StringUtils.hasText(generatedSql)) {
                return NodeResult.fail("generated sql is empty");
            }
            context.setGeneratedSql(generatedSql);
            context.putNodeOutput(WorkflowNodeCodes.SQL_GENERATE.getNodeCode(), "requestId", response == null ? null : response.getRequestId());
            context.put(WorkflowContextKeys.SqlGenerate.GENERATED_SQL, generatedSql);
            context.put(WorkflowContextKeys.SqlGenerate.REQUEST_ID, response == null ? null : response.getRequestId());
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()).setStatus("SUCCESS");
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.SQL_DRAFT.name(),
                    AiChatArtifactStage.SQL_GEN.name(),
                    "SQL 草案",
                    generatedSql,
                    AiChatContentFormat.SQL.name(),
                    true,
                    "SUCCESS",
                    context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                    response == null ? null : response.getRequestId()
            );
            return NodeResult.success(null);
        } catch (Exception ex) {
            log.error("sql generate failed, sessionCode={}", context.getSession() == null ? null : context.getSession().getSessionCode(), ex);
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()).setStatus("FAILED");
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.WORKFLOW_ERROR.name(),
                    AiChatArtifactStage.SQL_GEN.name(),
                    "SQL 生成失败",
                    ex.getMessage(),
                    AiChatContentFormat.PLAIN_TEXT.name(),
                    true,
                    "FAILED",
                    context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                    null
            );
            return NodeResult.fail(ex.getMessage());
        }
    }

    @Override
    public String code() {
        return WorkflowNodeCodes.SQL_GENERATE.getNodeCode();
    }

    @Override
    public int order() {
        return 400;
    }

    private SqlPreGenerateResult buildSqlPreGenerateResult(WorkflowContext context) {
        SqlPreGenerateResult result = new SqlPreGenerateResult();
        PlanningResult planningResult = context.get(WorkflowContextKeys.Planning.QUERY_PLAN_RESULT);
        result.setKnowledgeSearchResponse(context.get(WorkflowContextKeys.Capability.KNOWLEDGE_SEARCH_RESPONSE));
        if (planningResult == null) {
            addProblem(result, "planning_result_missing", "缺少查询规划结果", "请先完成 Query Planning 节点执行。", true, 1.0D);
            result.setConfidence(0.0D);
            return result;
        }

        buildMainTable(result, planningResult);
        buildRelationTables(result, planningResult);
        buildFilters(result, planningResult);
        attachKnowledgeHits(result);
        buildProblems(result, planningResult, context);
        result.setConfidence(calculateOverallConfidence(result));
        return result;
    }

    private void buildMainTable(SqlPreGenerateResult result, PlanningResult planningResult) {
        PlanningResult.Subject subject = planningResult.getSubject();
        SqlPreGenerateResult.MainTableStruct mainTable = result.getMainTable();
        if (subject == null) {
            addProblem(result, "main_table_missing", "未识别到主表对象", "请补充用户问题中的核心查询主体。", true, 1.0D);
            return;
        }
        mainTable.setTableName(firstNonBlank(subject.getName(), subject.getValue()));
        mainTable.setTableComment(buildSubjectComment(subject));
        mainTable.setConfidence(normalizeConfidence(subject.getScore()));
        if (!StringUtils.hasText(mainTable.getTableName())) {
            addProblem(result, "main_table_name_missing", "主表名称未确定", "请结合知识库表结构补充真实主表名。", true,
                    normalizeConfidence(subject.getScore()));
        }
    }

    private void buildRelationTables(SqlPreGenerateResult result, PlanningResult planningResult) {
        PlanningResult.Subject subject = planningResult.getSubject();
        if (subject == null || CollectionUtils.isEmpty(subject.getRelations())) {
            return;
        }
        for (PlanningResult.RelationItem relation : subject.getRelations()) {
            if (relation == null) {
                continue;
            }
            SqlPreGenerateResult.RelationTableStruct table = new SqlPreGenerateResult.RelationTableStruct();
            table.setTableName(firstNonBlank(relation.getName(), firstNonBlankFromList(relation.getValues())));
            table.setTableComment(buildRelationComment(relation));
            table.setRelationType("unknown");
            table.setRelationComment("当前仅识别到关联对象，尚未确定具体 join 条件。");
            table.setConfidence(normalizeConfidence(relation.getScore()));
            result.getRelationTables().add(table);

            addProblem(result,
                    "relation_join_unknown",
                    "关联表 " + firstNonBlank(table.getTableName(), "unknown") + " 的关联方式尚未确定",
                    "请结合知识库补充 join 类型和关联字段。",
                    false,
                    normalizeConfidence(relation.getScore()));
        }
    }

    private void buildFilters(SqlPreGenerateResult result, PlanningResult planningResult) {
        if (CollectionUtils.isEmpty(planningResult.getFilters())) {
            return;
        }
        String mainTableName = result.getMainTable() == null ? null : result.getMainTable().getTableName();
        for (PlanningResult.FilterItem filter : planningResult.getFilters()) {
            if (filter == null || !StringUtils.hasText(filter.getKey())) {
                continue;
            }
            SqlPreGenerateResult.FilterConditionStruct item = new SqlPreGenerateResult.FilterConditionStruct();
            item.setTableName(mainTableName);
            item.setFieldName(filter.getKey().trim());
            item.setOperator(inferOperator(filter.getValue()));
            item.setValue(filter.getValue());
            item.setConditionComment(buildFilterComment(filter));
            item.setConfidence(normalizeConfidence(filter.getScore()));
            result.getFilters().add(item);
        }
    }

    private void buildProblems(SqlPreGenerateResult result, PlanningResult planningResult, WorkflowContext context) {
        KbSearchResponse knowledgeSearchResponse = result.getKnowledgeSearchResponse();
        if (knowledgeSearchResponse == null || CollectionUtils.isEmpty(knowledgeSearchResponse.getItems())) {
            addProblem(result, "knowledge_context_missing", "当前未检索到可靠的知识库表结构上下文",
                    "请补充知识库表结构、字段说明和关联关系后再继续 SQL 生成。", false, 0.9D);
        }
        if (planningResult.getAmbiguity() != null && Boolean.TRUE.equals(planningResult.getAmbiguity().getHasAmbiguity())
                && !CollectionUtils.isEmpty(planningResult.getAmbiguity().getItems())) {
            for (PlanningResult.AmbiguityItem ambiguityItem : planningResult.getAmbiguity().getItems()) {
                if (ambiguityItem == null) {
                    continue;
                }
                addProblem(result,
                        firstNonBlank(ambiguityItem.getType(), "ambiguity"),
                        ambiguityItem.getQuestion(),
                        ambiguityItem.getSuggestion(),
                        ambiguityItem.getImportance() != null && ambiguityItem.getImportance() >= 8,
                        normalizeConfidence(toConfidence(ambiguityItem.getImportance())));
            }
        }
    }

    private void attachKnowledgeHits(SqlPreGenerateResult result) {
        KbSearchResponse knowledgeSearchResponse = result.getKnowledgeSearchResponse();
        if (knowledgeSearchResponse == null || CollectionUtils.isEmpty(knowledgeSearchResponse.getItems())) {
            return;
        }
        if (result.getMainTable() != null) {
            result.getMainTable().setKnowledgeHits(resolveKnowledgeHits(
                    knowledgeSearchResponse.getItems(),
                    result.getMainTable().getTableName(),
                    result.getMainTable().getTableComment(),
                    "main_table_match"
            ));
        }
        if (!CollectionUtils.isEmpty(result.getRelationTables())) {
            for (SqlPreGenerateResult.RelationTableStruct relationTable : result.getRelationTables()) {
                if (relationTable == null) {
                    continue;
                }
                relationTable.setKnowledgeHits(resolveKnowledgeHits(
                        knowledgeSearchResponse.getItems(),
                        relationTable.getTableName(),
                        relationTable.getTableComment(),
                        "relation_table_match"
                ));
            }
        }
    }

    private List<SqlPreGenerateResult.KnowledgeHitRef> resolveKnowledgeHits(List<KbSearchItem> items,
                                                                            String tableName,
                                                                            String tableComment,
                                                                            String reason) {
        if (CollectionUtils.isEmpty(items) || (!StringUtils.hasText(tableName) && !StringUtils.hasText(tableComment))) {
            return List.of();
        }
        Map<String, SqlPreGenerateResult.KnowledgeHitRef> matches = new LinkedHashMap<>();
        for (KbSearchItem item : items) {
            if (!isKnowledgeHitMatch(item, tableName, tableComment)) {
                continue;
            }
            SqlPreGenerateResult.KnowledgeHitRef ref = new SqlPreGenerateResult.KnowledgeHitRef();
            ref.setDocumentId(item == null ? null : item.getDocumentId());
            ref.setScore(item == null ? null : item.getScore());
            ref.setReason(reason);
            matches.put(ref.getDocumentId() == null ? String.valueOf(matches.size()) : ref.getDocumentId(), ref);
        }
        return new ArrayList<>(matches.values());
    }

    private boolean isKnowledgeHitMatch(KbSearchItem item, String tableName, String tableComment) {
        if (item == null) {
            return false;
        }
        String content = safeLower(item.getContent());
        String metadata = safeLower(item.getMetadata());
        return containsAny(content, metadata, tableName) || containsAny(content, metadata, tableComment);
    }

    private boolean containsAny(String content, String metadata, String probe) {
        if (!StringUtils.hasText(probe)) {
            return false;
        }
        String normalized = probe.trim().toLowerCase(Locale.ROOT);
        return content.contains(normalized) || metadata.contains(normalized);
    }

    private String safeLower(Object value) {
        return value == null ? "" : String.valueOf(value).toLowerCase(Locale.ROOT);
    }

    private String buildSubjectComment(PlanningResult.Subject subject) {
        List<String> comments = new ArrayList<>();
        if (StringUtils.hasText(subject.getValue()) && !Objects.equals(subject.getValue(), subject.getName())) {
            comments.add("原始主体：" + subject.getValue().trim());
        }
        if (!CollectionUtils.isEmpty(subject.getAliases())) {
            comments.add("别名：" + String.join(" / ", subject.getAliases()));
        }
        return comments.isEmpty() ? null : String.join("；", comments);
    }

    private String buildRelationComment(PlanningResult.RelationItem relation) {
        List<String> comments = new ArrayList<>();
        if (!CollectionUtils.isEmpty(relation.getValues())) {
            comments.add("取值：" + String.join(" / ", relation.getValues()));
        }
        if (!CollectionUtils.isEmpty(relation.getAliases())) {
            comments.add("别名：" + String.join(" / ", relation.getAliases()));
        }
        return comments.isEmpty() ? null : String.join("；", comments);
    }

    private String buildFilterComment(PlanningResult.FilterItem filter) {
        List<String> comments = new ArrayList<>();
        if (StringUtils.hasText(filter.getModel())) {
            comments.add("模型说明：" + filter.getModel().trim());
        }
        if (StringUtils.hasText(filter.getSource())) {
            comments.add("来源：" + filter.getSource().trim());
        }
        return comments.isEmpty() ? null : String.join("；", comments);
    }

    private void addProblem(SqlPreGenerateResult result,
                            String type,
                            String message,
                            String suggestion,
                            Boolean blocking,
                            Double confidence) {
        SqlPreGenerateResult.ProblemStruct problem = new SqlPreGenerateResult.ProblemStruct();
        problem.setType(type);
        problem.setMessage(message);
        problem.setSuggestion(suggestion);
        problem.setBlocking(blocking);
        problem.setConfidence(confidence);
        result.getProblems().add(problem);
    }

    private Double calculateOverallConfidence(SqlPreGenerateResult result) {
        List<Double> scores = new ArrayList<>();
        if (result.getMainTable() != null) {
            addConfidence(scores, result.getMainTable().getConfidence());
        }
        if (!CollectionUtils.isEmpty(result.getRelationTables())) {
            for (SqlPreGenerateResult.RelationTableStruct relationTable : result.getRelationTables()) {
                addConfidence(scores, relationTable == null ? null : relationTable.getConfidence());
            }
        }
        if (!CollectionUtils.isEmpty(result.getFilters())) {
            for (SqlPreGenerateResult.FilterConditionStruct filter : result.getFilters()) {
                addConfidence(scores, filter == null ? null : filter.getConfidence());
            }
        }
        if (scores.isEmpty()) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (Double score : scores) {
            sum += score;
        }
        return sum / scores.size();
    }

    private void addConfidence(List<Double> scores, Double value) {
        if (value != null) {
            scores.add(value);
        }
    }

    private Double normalizeConfidence(Double value) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            return 0.0D;
        }
        if (value > 1) {
            return 1.0D;
        }
        return value;
    }

    private Double toConfidence(Integer importance) {
        if (importance == null) {
            return null;
        }
        return Math.min(1.0D, Math.max(0.0D, importance / 10.0D));
    }

    private String inferOperator(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.contains(",")) {
            return "IN";
        }
        if (value.contains("~") || value.contains("至")) {
            return "BETWEEN";
        }
        return "=";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String firstNonBlankFromList(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private ChatRequest buildRequest(AiChatQueryCommand command, WorkflowContext context) {
        ChatRequest request = new ChatRequest();
        request.setProvider(resolveProviderType(command.getApiModel()));
        request.setModel(resolveActualModel(command.getApiModel()));

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(buildMessage(MessageRole.SYSTEM, SQL_GENERATION_PROMPT));
        messages.add(buildMessage(MessageRole.USER, buildSqlGenerationInput(command, context)));
        request.setMessages(messages);

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(resolveMaxTokens(command.getApiModel()));
        options.setTimeoutMs(30_000);
        request.setOptions(options);

        RequestMeta meta = new RequestMeta();
        meta.setTraceId(command.getTraceId());
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() : DEFAULT_SCENE);
        request.setMeta(meta);
        return request;
    }

    private ChatMessage buildMessage(MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private String buildSqlGenerationInput(AiChatQueryCommand command, WorkflowContext context) {
        StringBuilder builder = new StringBuilder();
        List<AiChatMessageDTO> sessionMessages = context.getOrCreateUserMessageContext().getSessionMessages();
        builder.append("用户问题：\n").append(command.getMessage()).append("\n\n");
        builder.append("查询规划：\n").append(context.getAnalysisResult()).append("\n\n");
        appendStructuredSection(builder, "SQL 生成规范", context.get(SQL_GENERATION_POLICY_KEY));
        appendStructuredSection(builder, "用户偏好", context.get(USER_PREFERENCE_KEY));
        if (StringUtils.hasText(context.getPromptContext(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()))) {
            builder.append("Prompt 上下文：\n")
                    .append(context.getPromptContext(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()))
                    .append("\n\n");
        }
        if (StringUtils.hasText(context.getKnowledgeResult())) {
            builder.append("知识上下文：\n").append(context.getKnowledgeResult()).append("\n\n");
        }
        if (!CollectionUtils.isEmpty(sessionMessages)) {
            builder.append("历史消息：\n");
            for (int i = 0; i < sessionMessages.size(); i++) {
                builder.append(i + 1)
                        .append(". ")
                        .append(sessionMessages.get(i).getRole())
                        .append(": ")
                        .append(sessionMessages.get(i).getContent())
                        .append('\n');
            }
        }
        return builder.toString();
    }

    private void appendStructuredSection(StringBuilder builder, String title, Object value) {
        String rendered = renderValue(value, 0);
        if (!StringUtils.hasText(rendered)) {
            return;
        }
        builder.append(title).append("：\n").append(rendered).append("\n\n");
    }

    private String renderValue(Object value, int indent) {
        if (value == null) {
            return "";
        }
        if (value instanceof String str) {
            return str.trim();
        }
        String indentText = "  ".repeat(Math.max(0, indent));
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String renderedChild = renderValue(entry.getValue(), indent + 1);
                if (!StringUtils.hasText(renderedChild)) {
                    continue;
                }
                builder.append(indentText)
                        .append("- ")
                        .append(entry.getKey())
                        .append(": ");
                if (entry.getValue() instanceof Map<?, ?> || entry.getValue() instanceof List<?>) {
                    builder.append('\n').append(renderedChild).append('\n');
                } else {
                    builder.append(renderedChild).append('\n');
                }
            }
            return builder.toString().trim();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object item : list) {
                String renderedChild = renderValue(item, indent + 1);
                if (!StringUtils.hasText(renderedChild)) {
                    continue;
                }
                builder.append(indentText).append("- ").append(renderedChild).append('\n');
            }
            return builder.toString().trim();
        }
        return String.valueOf(value);
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

    private String normalizeSql(String sql) {
        if (!StringUtils.hasText(sql)) {
            return sql;
        }
        String normalized = sql.trim();
        normalized = normalized.replace("```sql", "");
        normalized = normalized.replace("```SQL", "");
        normalized = normalized.replace("```", "");
        return normalized.trim();
    }

    private ProviderType resolveProviderType(String apiModel) {
        AiModelConfigDTO config = findModelConfigByApiModel(apiModel);
        if (config != null && StringUtils.hasText(config.getProviderCode())) {
            try {
                return ProviderType.valueOf(config.getProviderCode().trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                return ProviderType.DASHSCOPE;
            }
        }
        return ProviderType.DASHSCOPE;
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
}
