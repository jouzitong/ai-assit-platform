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
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * SQL 预生成节点，负责基于规划和知识上下文生成预生成结构与伪 SQL。
 *
 * <p>功能：</p>
 * <ul>
 *     <li>消费用户问题、查询规划、知识上下文和必要历史消息。</li>
 *     <li>调用模型生成 SQL 预生成结构。</li>
 *     <li>调用模型生成伪 SQL 草案。</li>
 *     <li>记录预生成结果与伪 SQL artifact，供后续真实 SQL 生成阶段继续处理。</li>
 * </ul>
 *
 * <p>边界描述：</p>
 * <ul>
 *     <li>只负责生成预生成结果与伪 SQL，不负责真实可执行 SQL。</li>
 *     <li>不负责知识检索，不负责真实执行，不负责最终答案渲染。</li>
 *     <li>即使携带假设说明，也只输出伪 SQL 文本，不扩展成解释性回答。</li>
 * </ul>
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Service
@Slf4j
public class SqlPreGenerateNode extends BaseWorkflowNode {

    private static final String DEFAULT_SCENE = "ai-chat-sql-pre-generate";
    private static final String DEFAULT_KB_ID = "w05enpcxa4";
    private static final int DEFAULT_KB_TOP_K = 20;
    private static final int PRE_GENERATE_PARSE_MAX_RETRY = 2;
    private static final String SQL_GENERATION_POLICY_KEY = "sqlGenerationPolicy";
    private static final String USER_PREFERENCE_KEY = "resolvedUserPreferences";
    private static final String SQL_PRE_GENERATE_PROMPT = """
            你是 SQL 预生成分析节点。
            你的任务不是生成 SQL，而是基于“查询规划”和“知识库命中文档”抽取后续 SQL 生成必须依赖的物理表信息。

            规则：
            1. 只能根据知识库命中文档中的明确证据，输出数据库真实表名。
            2. query plan 中的主体、关联对象、过滤条件只是语义线索，不能直接当成物理表名。
            3. 如果知识库文档无法明确支持某个物理表名、关联方式或过滤字段，必须留空，并在 problems 中说明。
            4. mainTable.tableName 和 relationTables[*].tableName 必须是数据库中可直接查询的真实表名。
            5. knowledgeHits 只允许引用当前输入里出现的 documentId。
            6. 如果无法确认，就输出 null 或空数组，不要猜。
            7. 只输出严格合法 JSON，不要输出 markdown、解释、代码块。

            JSON 结构固定为：
            {
              "mainTable": {
                "tableName": "真实物理表名，无法确认时为 null",
                "tableComment": "主表说明，可为 null",
                "confidence": 0.0,
                "knowledgeHits": [
                  {
                    "documentId": "命中文档ID",
                    "score": 0.0,
                    "reason": "为什么这个文档支撑主表判断"
                  }
                ]
              },
              "relationTables": [
                {
                  "tableName": "真实物理表名，无法确认时为 null",
                  "tableComment": "关联表说明，可为 null",
                  "relationType": "如 left_join / inner_join，无法确认时为 null",
                  "relationComment": "关联说明，可为 null",
                  "confidence": 0.0,
                  "knowledgeHits": [
                    {
                      "documentId": "命中文档ID",
                      "score": 0.0,
                      "reason": "为什么这个文档支撑关联表判断"
                    }
                  ]
                }
              ],
              "filters": [
                {
                  "tableName": "真实物理表名，无法确认时可为 null",
                  "fieldName": "真实物理字段名，无法确认时可为 null",
                  "operator": "= / in / between / like 等，无法确认时可为 null",
                  "value": "过滤值，可为 null",
                  "conditionComment": "过滤条件说明，可为 null",
                  "confidence": 0.0
                }
              ],
              "problems": [
                {
                  "type": "问题类型",
                  "message": "问题描述",
                  "suggestion": "建议方案",
                  "blocking": false,
                  "confidence": 0.0
                }
              ],
              "confidence": 0.0
            }
            """;
    private static final String SQL_GENERATION_PROMPT = """
            你是一个 SQL 预生成节点。
            请严格根据提供的用户问题、查询规划、预生成结构和知识上下文，生成一条伪 SQL。
            你还会收到“SQL 生成规范”和“用户偏好”两个补充部分：
            - SQL 生成规范属于硬约束，必须遵守
            - 用户偏好属于软参考，仅在不与用户本轮要求和硬约束冲突时采用

            约束要求：
            1. 这是伪 SQL，不要求一定能直接执行
            2. 只允许输出单条 SELECT 或 WITH 风格伪 SQL
            3. 不允许输出 INSERT、UPDATE、DELETE、DROP、ALTER、TRUNCATE、CREATE、MERGE
            4. 如果真实表名、字段名还不完整，可以保留占位或在 SQL 前用单行注释说明假设
            5. 最终输出只包含伪 SQL 文本，可带 SQL 注释，不要解释
            """;

    private final AiChatExecutionApi aiChatExecutionApi;
    private final AiMetaQueryApi aiMetaQueryApi;
    private final WorkflowHistoryRecorder historyRecorder;
    private final ObjectMapper objectMapper;

    public SqlPreGenerateNode(AiChatExecutionApi aiChatExecutionApi,
                              AiMetaQueryApi aiMetaQueryApi,
                              WorkflowHistoryRecorder historyRecorder,
                              ObjectMapper objectMapper) {
        this.aiChatExecutionApi = aiChatExecutionApi;
        this.aiMetaQueryApi = aiMetaQueryApi;
        this.historyRecorder = historyRecorder;
        this.objectMapper = objectMapper;
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
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode()).setRequest(request);
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode()).setStatus("RUNNING");
            ChatResponse response = aiChatExecutionApi.chat(request).getData();
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode()).setResponse(response);
            String generatedSql = normalizeSql(extractAnswer(response));
            if (!StringUtils.hasText(generatedSql)) {
                return NodeResult.fail("pseudo sql is empty");
            }
            context.setGeneratedSql(generatedSql);
            context.putNodeOutput(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), "requestId", response == null ? null : response.getRequestId());
            context.put(WorkflowContextKeys.SqlGenerate.GENERATED_SQL, generatedSql);
            context.put(WorkflowContextKeys.SqlGenerate.REQUEST_ID, response == null ? null : response.getRequestId());
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode()).setStatus("SUCCESS");
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.SQL_DRAFT.name(),
                    AiChatArtifactStage.SQL_GEN.name(),
                    "伪 SQL 草案",
                    generatedSql,
                    AiChatContentFormat.SQL.name(),
                    true,
                    "SUCCESS",
                    context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                    response == null ? null : response.getRequestId()
            );
            return NodeResult.success(null);
        } catch (Exception ex) {
            log.error("sql pre-generate failed, sessionCode={}", context.getSession() == null ? null : context.getSession().getSessionCode(), ex);
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode()).setStatus("FAILED");
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.WORKFLOW_ERROR.name(),
                    AiChatArtifactStage.SQL_GEN.name(),
                    "SQL 预生成失败",
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
        return WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode();
    }

    @Override
    public int order() {
        return 400;
    }

    private SqlPreGenerateResult buildSqlPreGenerateResult(WorkflowContext context) {
        PlanningResult planningResult = context.get(WorkflowContextKeys.Planning.QUERY_PLAN_RESULT);
        KbSearchResponse knowledgeSearchResponse = context.get(WorkflowContextKeys.Capability.KNOWLEDGE_SEARCH_RESPONSE);
        SqlPreGenerateResult result = new SqlPreGenerateResult();
        result.setKnowledgeSearchResponse(knowledgeSearchResponse);
        if (planningResult == null) {
            addProblem(result, "planning_result_missing", "缺少查询规划结果", "请先完成 Query Planning 节点执行。", true, 1.0D);
            result.setConfidence(0.0D);
            return result;
        }
        if (knowledgeSearchResponse == null || CollectionUtils.isEmpty(knowledgeSearchResponse.getItems())) {
            addProblem(result, "knowledge_context_missing", "当前未检索到可靠的知识库表结构上下文",
                    "请补充知识库表结构、字段说明和关联关系后再继续 SQL 生成。", false, 0.9D);
            result.setConfidence(0.0D);
            return result;
        }
        try {
            SqlPreGenerateResult analyzed = analyzeSqlPreGenerateResult(context, planningResult, knowledgeSearchResponse);
            analyzed.setKnowledgeSearchResponse(knowledgeSearchResponse);
            backfillKnowledgeHitScores(analyzed, knowledgeSearchResponse);
            if (analyzed.getMainTable() == null) {
                analyzed.setMainTable(new SqlPreGenerateResult.MainTableStruct());
            }
            if (analyzed.getRelationTables() == null) {
                analyzed.setRelationTables(new ArrayList<>());
            }
            if (analyzed.getFilters() == null) {
                analyzed.setFilters(new ArrayList<>());
            }
            if (analyzed.getProblems() == null) {
                analyzed.setProblems(new ArrayList<>());
            }
            return analyzed;
        } catch (Exception ex) {
            log.warn("sql pre-generate analyze failed, fallback to minimal result, error={}", ex.getMessage());
            addProblem(result, "sql_pre_generate_analyze_failed", ex.getMessage(),
                    "请检查知识库文档结构，或重试预生成分析。", true, 1.0D);
            result.setConfidence(0.0D);
            return result;
        }
    }

    private SqlPreGenerateResult analyzeSqlPreGenerateResult(WorkflowContext context,
                                                             PlanningResult planningResult,
                                                             KbSearchResponse knowledgeSearchResponse) {
        String currentText = extractAnswer(aiChatExecutionApi.chat(buildSqlPreGenerateRequest(context, planningResult, knowledgeSearchResponse)).getData());
        String validationError = null;
        for (int attempt = 0; attempt <= PRE_GENERATE_PARSE_MAX_RETRY; attempt++) {
            try {
                SqlPreGenerateResult result = objectMapper.readValue(cleanJson(currentText), SqlPreGenerateResult.class);
                validateSqlPreGenerateResult(result);
                return result;
            } catch (Exception ex) {
                validationError = ex.getMessage();
                if (attempt == PRE_GENERATE_PARSE_MAX_RETRY) {
                    throw new IllegalArgumentException("sql pre-generate result parse failed: " + validationError, ex);
                }
                currentText = retrySqlPreGenerateWithFeedback(context, planningResult, knowledgeSearchResponse, currentText, validationError);
            }
        }
        throw new IllegalArgumentException("sql pre-generate result parse failed: " + validationError);
    }

    private ChatRequest buildSqlPreGenerateRequest(WorkflowContext context,
                                                   PlanningResult planningResult,
                                                   KbSearchResponse knowledgeSearchResponse) {
        AiChatQueryCommand command = context.getCommand();
        ChatRequest request = new ChatRequest();
        request.setProvider(resolveProviderType(command == null ? null : command.getApiModel()));
        request.setModel(resolveActualModel(command == null ? null : command.getApiModel()));

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(buildMessage(MessageRole.SYSTEM, SQL_PRE_GENERATE_PROMPT));
        messages.add(buildMessage(MessageRole.USER, buildSqlPreGenerateInput(context, planningResult, knowledgeSearchResponse)));
        request.setMessages(messages);

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(Math.max(1024, resolveMaxTokens(command == null ? null : command.getApiModel())));
        options.setTimeoutMs(30_000);
        request.setOptions(options);

        RequestMeta meta = new RequestMeta();
        meta.setTraceId(command == null ? null : command.getTraceId());
        meta.setScene("ai-chat-sql-pre-generate");
        request.setMeta(meta);
        return request;
    }

    private String buildSqlPreGenerateInput(WorkflowContext context,
                                            PlanningResult planningResult,
                                            KbSearchResponse knowledgeSearchResponse) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户问题：\n").append(defaultText(context.getCommand() == null ? null : context.getCommand().getMessage())).append("\n\n");
        builder.append("查询规划摘要：\n").append(defaultText(context.getAnalysisResult())).append("\n\n");
        try {
            builder.append("查询规划 JSON：\n").append(objectMapper.writeValueAsString(planningResult)).append("\n\n");
        } catch (Exception ex) {
            builder.append("查询规划 JSON：\n").append(String.valueOf(planningResult)).append("\n\n");
        }
        builder.append("知识库命中文档：\n").append(renderKnowledgeHits(knowledgeSearchResponse)).append("\n");
        return builder.toString().trim();
    }

    private String renderKnowledgeHits(KbSearchResponse knowledgeSearchResponse) {
        if (knowledgeSearchResponse == null || CollectionUtils.isEmpty(knowledgeSearchResponse.getItems())) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (KbSearchItem item : knowledgeSearchResponse.getItems()) {
            if (item == null) {
                continue;
            }
            builder.append(index++).append(". ")
                    .append("documentId=").append(defaultText(item.getDocumentId()))
                    .append(", score=").append(item.getScore())
                    .append(", metadata=").append(item.getMetadata())
                    .append('\n')
                    .append(defaultText(item.getContent()))
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    private String retrySqlPreGenerateWithFeedback(WorkflowContext context,
                                                   PlanningResult planningResult,
                                                   KbSearchResponse knowledgeSearchResponse,
                                                   String previousOutput,
                                                   String validationError) {
        ChatRequest retryRequest = buildSqlPreGenerateRequest(context, planningResult, knowledgeSearchResponse);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(buildMessage(MessageRole.SYSTEM, SQL_PRE_GENERATE_PROMPT));
        messages.add(buildMessage(MessageRole.ASSISTANT, defaultText(previousOutput)));
        messages.add(buildMessage(MessageRole.USER, """
                你上一次返回的 JSON 不合法，请严格修正后重新返回。
                校验错误：
                %s
                额外要求：
                - 只返回 JSON
                - 不允许代码块
                - tableName 必须来自知识库文档里的真实物理表名，不能使用 query plan 中的中文主体名
                - knowledgeHits.documentId 只能引用当前输入里已有的 documentId
                """.formatted(validationError)));
        retryRequest.setMessages(messages);
        return extractAnswer(aiChatExecutionApi.chat(retryRequest).getData());
    }

    private void validateSqlPreGenerateResult(SqlPreGenerateResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result is null");
        }
        if (result.getMainTable() == null) {
            throw new IllegalArgumentException("mainTable is required");
        }
        if (result.getRelationTables() == null) {
            throw new IllegalArgumentException("relationTables is required");
        }
        if (result.getFilters() == null) {
            throw new IllegalArgumentException("filters is required");
        }
        if (result.getProblems() == null) {
            throw new IllegalArgumentException("problems is required");
        }
        if (result.getMainTable().getKnowledgeHits() == null) {
            result.getMainTable().setKnowledgeHits(new ArrayList<>());
        }
        for (SqlPreGenerateResult.RelationTableStruct relationTable : result.getRelationTables()) {
            if (relationTable != null && relationTable.getKnowledgeHits() == null) {
                relationTable.setKnowledgeHits(new ArrayList<>());
            }
        }
    }

    private void backfillKnowledgeHitScores(SqlPreGenerateResult result, KbSearchResponse knowledgeSearchResponse) {
        if (result == null || knowledgeSearchResponse == null || CollectionUtils.isEmpty(knowledgeSearchResponse.getItems())) {
            return;
        }
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        for (KbSearchItem item : knowledgeSearchResponse.getItems()) {
            if (item != null && StringUtils.hasText(item.getDocumentId())) {
                scoreMap.put(item.getDocumentId(), item.getScore());
            }
        }
        if (result.getMainTable() != null) {
            backfillKnowledgeHitScores(result.getMainTable().getKnowledgeHits(), scoreMap);
        }
        if (!CollectionUtils.isEmpty(result.getRelationTables())) {
            for (SqlPreGenerateResult.RelationTableStruct relationTable : result.getRelationTables()) {
                if (relationTable != null) {
                    backfillKnowledgeHitScores(relationTable.getKnowledgeHits(), scoreMap);
                }
            }
        }
    }

    private void backfillKnowledgeHitScores(List<SqlPreGenerateResult.KnowledgeHitRef> refs, Map<String, Double> scoreMap) {
        if (CollectionUtils.isEmpty(refs)) {
            return;
        }
        for (SqlPreGenerateResult.KnowledgeHitRef ref : refs) {
            if (ref == null || ref.getScore() != null || !StringUtils.hasText(ref.getDocumentId())) {
                continue;
            }
            ref.setScore(scoreMap.get(ref.getDocumentId()));
        }
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

    private String cleanJson(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("sql pre-generate output is empty");
        }
        String cleaned = text.trim();
        cleaned = cleaned.replace("```json", "");
        cleaned = cleaned.replace("```JSON", "");
        cleaned = cleaned.replace("```", "");
        return cleaned.trim();
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
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
        builder.append("SQL 预生成结果：\n").append(String.valueOf(context.getSqlPreGenerateResult())).append("\n\n");
        appendStructuredSection(builder, "SQL 生成规范", context.get(SQL_GENERATION_POLICY_KEY));
        appendStructuredSection(builder, "用户偏好", context.get(USER_PREFERENCE_KEY));
        if (StringUtils.hasText(context.getPromptContext(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode()))) {
            builder.append("Prompt 上下文：\n")
                    .append(context.getPromptContext(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode()))
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
