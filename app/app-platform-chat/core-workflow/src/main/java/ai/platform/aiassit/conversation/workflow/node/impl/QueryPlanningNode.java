package ai.platform.aiassit.conversation.workflow.node.impl;

import ai.platform.aiassit.conversation.constant.ConversationEventPhases;
import ai.platform.aiassit.conversation.constant.ConversationEventSources;
import ai.platform.aiassit.service.ai.api.dto.ChatMessage;
import ai.platform.aiassit.service.ai.api.dto.ChatOptions;
import ai.platform.aiassit.service.ai.api.dto.ChatRequest;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.dto.OutputItem;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.enums.MessageRole;
import ai.platform.aiassit.service.ai.api.enums.ProviderType;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.workflow.bean.NodeResult;
import ai.platform.aiassit.conversation.workflow.config.WorkflowProperties;
import ai.platform.aiassit.conversation.workflow.constants.ConversationRuntimeContextKeys;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.conversation.workflow.node.BaseWorkflowNode;
import ai.platform.aiassit.conversation.workflow.planning.contract.IntentAnalysisBundle;
import ai.platform.aiassit.conversation.workflow.planning.contract.IntentEvidence;
import ai.platform.aiassit.conversation.workflow.planning.contract.PlanningContextMessage;
import ai.platform.aiassit.conversation.workflow.planning.contract.PlanningExtKeys;
import ai.platform.aiassit.conversation.workflow.planning.contract.PlanningResult;
import ai.platform.aiassit.conversation.workflow.planning.skill.QueryPlanningSkillExecutor;
import ai.platform.aiassit.conversation.workflow.support.WorkflowHistoryRecorder;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactStage;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import ai.platform.aiassit.execution.service.AiExecutionDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * 查询规划节点。
 *
 * <p>功能：</p>
 * <ul>
 *     <li>基于当前用户问题、历史消息和技能分析结果，提炼查询意图。</li>
 *     <li>调用模型生成结构化规划结果，并做解析、校验、必要重试。</li>
 *     <li>沉淀用户目标、分析摘要、所需上下文、SQL 关注点、风险等规划产物。</li>
 *     <li>记录规划类 artifact，并为后续节点提供稳定的结构化规划结果。</li>
 * </ul>
 *
 * <p>边界描述：</p>
 * <ul>
 *     <li>只回答“要查什么、为什么这样查、后续需要什么上下文”。</li>
 *     <li>不直接执行知识检索，不生成 SQL，不执行 SQL。</li>
 *     <li>不组织最终用户回复，规划结果统一通过 {@link ConversationRuntimeContext} 传递。</li>
 * </ul>
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Service
@Slf4j
public class QueryPlanningNode extends BaseWorkflowNode {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DEFAULT_SCENE = "ai-chat-query-planning";
    private static final String PLANNING_TOPIC_PROMPT = """
            你是一个智能问数工作流的查询规划节点。
            你需要根据用户当前问题和已有对话历史，提炼本轮查询的主体、条件、意图、展示方式、歧义点、置信度和扩展信息。
            你的输出会直接作为后续知识检索、SQL 生成和结果渲染节点的输入，因此内容必须准确、简洁、可执行。
            """;
    private static final String PLANNING_STRUCTURE_PROMPT = """
            你必须只输出严格合法的 JSON，不允许输出 markdown、解释、代码块。
            这是语义规划阶段，不是物理建模阶段。不要猜表名、字段名、join 路径、接口参数，也不要为了凑结构而编造不存在的信息。
            输出结构固定如下：
            {
              "title": "规划标题，建议返回简洁标题",
              "subject": {
                "name": "主体名称，表示可能需要的核心表信息说明，如人员、个人信息、员工档案",
                "value": "主体识别值，如张三、某部门、某项目",
                "aliases": ["主体近似词、别名、同义词，用于知识库召回"],
                "score": 0.93,
                "relations": [
                  {
                    "name": "关联信息名称，表示主体所处 scope 或关联范围说明，如公司信息、组织信息、部门信息",
                    "values": ["关联值1", "关联值2"],
                    "aliases": ["关联近似词、别名、同义词"],
                    "score": 0.86
                  }
                ]
              },
              "filters": [
                {
                  "key": "条件 key，描述过滤条件核心语义，如 company、person_name、time_range",
                  "value": "条件值",
                  "model": "可能的模型说明，如公司信息、人员信息、组织信息",
                  "source": "判断依据来源，如 user_input、history、rule、retrieval",
                  "score": 0.92
                }
              ],
              "intent": {
                "type": "意图类型，支持多个值，多个值之间使用英文逗号分隔，如 detail,list 或 statistics,profile",
                "name": "意图中文名称",
                "action": "可执行动作描述，支持多个值，多个值之间使用英文逗号分隔，如 count,query_detail,query_list",
                "score": 0.96
              },
              "render": {
                "type": "展示类型，支持多个值，多个值之间使用英文逗号分隔，如 table,detail 或 profile,dashboard",
                "name": "展示中文名称",
                "score": 0.88
              },
              "ambiguity": {
                "hasAmbiguity": false,
                "items": [
                  {
                    "type": "歧义类型，如 duplicate_name、missing_time_range、unclear_caliber",
                    "question": "建议澄清问题",
                    "importance": 3,
                    "suggestion": "建议补充内容说明"
                  }
                ]
              },
              "ext": {
                "metrics": ["可能涉及的指标"],
                "dimensions": ["可能涉及的维度"],
                "sort": ["排序建议，或排序字段加方向"],
                "topN": 10,
                "statisticalCaliber": ["统计口径说明"],
                "semanticTerms": ["语义词及其归一化说明"],
                "其他扩展字段": "允许继续补充，但 key 需要语义清晰"
              }
            }
            
            语义说明：
            1. subject 表示本轮主要查询对象，即后续最可能围绕谁、哪类信息展开查询。
            2. subject.relations 表示主体的 scope 或关联范围，不是物理关联表说明，而是语义上的范围限定、归属信息、上下文边界。
               例如“查询上海公司的张三个人画像”中，主体可以是“人员/张三”，而“上海公司”更适合作为 subject.relations 中的 scope 信息。
            3. filters 表示显式过滤条件；如果某个范围信息既像 scope 又像过滤条件，优先按对主体的限定关系判断：
               - 更像主体归属或范围边界的，放 subject.relations
               - 更像直接筛选条件的，放 filters
            4. score 不是随意填写的装饰字段，必须根据当前问题、历史上下文、技能证据、语义明确程度给出经验判断。
               - 识别非常明确、几乎无歧义时，分数应更高
               - 只有弱线索、存在明显歧义或只是推测时，分数应降低
               - 如果缺少依据，不要给高分
            5. 严禁胡编乱造。如果用户没有提供、上下文没有支持、技能证据没有覆盖，就宁可留空、降分、或在 ambiguity 中提出澄清问题，也不要编造主体、scope、条件或扩展信息。

            字段要求：
            1. title、subject、filters、intent、render、ambiguity、ext 必须全部返回
            2. subject.name、subject.value 必须为非空字符串
            3. subject.aliases、subject.relations、subject.relations[*].values、subject.relations[*].aliases、ambiguity.items 必须为 JSON 数组，可为空数组
            4. intent.type、intent.name、render.type、render.name 必须为非空字符串
            5. ambiguity.hasAmbiguity 必须为布尔值；当其为 true 时，items 至少返回一项
            6. 如果是新会话首轮，title 必须给出 6 到 20 个字的简洁标题
            7. aliases 重点补充可用于知识库关键词匹配的近似词、简称、别名、业务叫法
            8. subject.score、subject.relations[*].score、filters[*].score、intent.score、render.score 都使用 0 到 1 之间的小数，并且必须体现真实判断把握，不允许机械统一打高分
            9. ext 是动态扩展区，优先使用这些 key：metrics、dimensions、sort、topN、statisticalCaliber、semanticTerms
            10. ext 中如果补充其他字段，必须保证 key 语义清晰、value 为合法 JSON 值
            11. 不要输出任何额外字段到 ext 之外
            12. intent.type 和 intent.action 如果有多个值，必须使用英文逗号分隔，不要返回 JSON 数组
            13. render.type 如果有多个值，必须使用英文逗号分隔，不要返回 JSON 数组
            14. 无法确认时，优先降低 score、减少编造、补充 ambiguity，而不是强行补全
            """;

    private final AiExecutionDomainService aiExecutionDomainService;
    private final WorkflowHistoryRecorder historyRecorder;
    private final ObjectMapper objectMapper;
    private final WorkflowProperties workflowProperties;
    private final QueryPlanningSkillExecutor queryPlanningSkillExecutor;

    public QueryPlanningNode(AiExecutionDomainService aiExecutionDomainService,
                             WorkflowHistoryRecorder historyRecorder,
                             ObjectMapper objectMapper,
                             WorkflowProperties workflowProperties,
                             QueryPlanningSkillExecutor queryPlanningSkillExecutor) {
        this.aiExecutionDomainService = aiExecutionDomainService;
        this.historyRecorder = historyRecorder;
        this.objectMapper = objectMapper;
        this.workflowProperties = workflowProperties;
        this.queryPlanningSkillExecutor = queryPlanningSkillExecutor;
    }

    @Override
    protected NodeResult doExecute(ConversationRuntimeContext context) {
        ConversationQueryCommand command = context.getCommand();
        if (command == null) {
            return NodeResult.fail("command is required");
        }
        if (context.getSession() == null) {
            return NodeResult.fail("session is required");
        }
        if (context.getRound() == null) {
            return NodeResult.fail("round is required");
        }
        if (context.getOrCreateUserMessageContext().getCurrentMessage() == null) {
            return NodeResult.fail("currentUserMessage is required");
        }
        if (!StringUtils.hasText(command.getMessage())) {
            return NodeResult.fail("message is required");
        }

        log.info("query planning node start, sessionCode={}, roundCode={}, messageCode={}, apiModel={}, scene={}",
                context.getSession().getSessionCode(),
                context.getRound().getRoundCode(),
                context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                command.getApiModel(),
                command.getScene());

        try {
            AiChatMessageDTO currentUserMessage = context.getOrCreateUserMessageContext().getCurrentMessage();
            List<AiChatMessageDTO> historyMessages = new ArrayList<>(context.getOrCreateUserMessageContext().getSessionMessages());
            historyMessages = historyMessages.stream()
                    .filter(message -> currentUserMessage == null
                            || !Objects.equals(message.getMessageCode(), currentUserMessage.getMessageCode()))
                    .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                    .toList();

            log.debug("query planning history prepared, sessionCode={}, historyMessageCount={}, isFirstRound={}",
                    context.getSession().getSessionCode(),
                    historyMessages.size(),
                    historyMessages.isEmpty());

            IntentAnalysisBundle intentAnalysisBundle = queryPlanningSkillExecutor.analyze(context);
            context.put(ConversationRuntimeContextKeys.Planning.INTENT_ANALYSIS_BUNDLE, intentAnalysisBundle);
            context.put(ConversationRuntimeContextKeys.Planning.QUERY_PLANNING_EVIDENCES, intentAnalysisBundle.getEvidences());
            context.put(ConversationRuntimeContextKeys.Planning.QUERY_PLANNING_CONTEXT_MESSAGES, intentAnalysisBundle.getContextMessages());
            context.publishProgressEvent(
                    ConversationEventSources.INTENT_ANALYZE,
                    ConversationEventPhases.READY,
                    "intent analysis bundle prepared"
            );

            ChatRequest planningRequest = buildPlanningRequest(command, context, historyMessages);
            log.info("query planning request built, sessionCode={}, provider={}, model={}, messageCount={}",
                    context.getSession().getSessionCode(),
                    planningRequest.getProvider(),
                    planningRequest.getModel(),
                    CollectionUtils.isEmpty(planningRequest.getMessages()) ? 0 : planningRequest.getMessages().size());

            ChatResponse planningResponse = aiExecutionDomainService.chat(planningRequest);
            if (planningResponse == null) {
                log.error("query planning api call failed, sessionCode={}, roundCode={}, response={}",
                        context.getSession().getSessionCode(),
                        context.getRound().getRoundCode(),
                        null);
            }
            log.info("query planning api call finished, sessionCode={}, roundCode={}, requestId={}, hasOutput={}",
                    context.getSession().getSessionCode(),
                    context.getRound().getRoundCode(),
                    planningResponse == null ? null : planningResponse.getRequestId(),
                    planningResponse != null && !CollectionUtils.isEmpty(planningResponse.getOutputs()));

            PlanningResult planningResult = parsePlanningResult(
                    extractAnswer(planningResponse),
                    historyMessages.isEmpty()
            );
            log.info("query planning result parsed, sessionCode={}, roundCode={}, sessionTitle={}, needClarification={}",
                    context.getSession().getSessionCode(),
                    context.getRound().getRoundCode(),
                    planningResult.getTitle(),
                    planningResult.getAmbiguity() != null && Boolean.TRUE.equals(planningResult.getAmbiguity().getHasAmbiguity()));
            String analysisResult = buildAnalysisSummary(planningResult);

            context.getOrCreateNodeResult(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode()).setRequest(planningRequest);
            context.getOrCreateNodeResult(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode()).setResponse(planningResponse);
            context.getOrCreateNodeResult(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode()).setStatus(STATUS_SUCCESS);
            context.putNodeOutput(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(), "planningResult", planningResult);
            context.putNodeOutput(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(), "planningRequestId",
                    planningResponse == null ? null : planningResponse.getRequestId());
            context.setAnalysisResult(analysisResult);
            context.put(ConversationRuntimeContextKeys.Planning.QUERY_PLAN, analysisResult);
            context.put(ConversationRuntimeContextKeys.Planning.QUERY_PLAN_RESULT, planningResult);
            context.put(ConversationRuntimeContextKeys.Planning.QUERY_PLANNING_SUMMARY, buildIntentAnalysisSummary(intentAnalysisBundle));
            context.put(ConversationRuntimeContextKeys.Planning.PLANNING_REQUEST_ID, planningResponse == null ? null : planningResponse.getRequestId());
            context.publishProgressEvent(
                    ConversationEventSources.QUERY_PLAN,
                    ConversationEventPhases.READY,
                    "query plan prepared"
            );
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.QUERY_PLAN.name(),
                    AiChatArtifactStage.PLAN.name(),
                    "查询规划",
                    planningResult,
                    AiChatContentFormat.JSON.name(),
                    true,
                    STATUS_SUCCESS,
                    context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                    planningResponse == null ? null : planningResponse.getRequestId()
            );
            log.info("query planning node success, sessionCode={}, roundCode={}, requestId={}",
                    context.getSession().getSessionCode(),
                    context.getRound().getRoundCode(),
                    planningResponse == null ? null : planningResponse.getRequestId());

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
                    context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                    null
            );
            context.getOrCreateNodeResult(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode()).setStatus(STATUS_FAILED);
            return NodeResult.fail(ex.getMessage());
        }
    }

    @Override
    public String code() {
        return WorkflowNodeCodes.QUERY_PLANNING.getNodeCode();
    }

    @Override
    public int order() {
        return 200;
    }

    private ChatRequest buildPlanningRequest(ConversationQueryCommand command,
                                             ConversationRuntimeContext context,
                                             List<AiChatMessageDTO> historyMessages) {
        ChatRequest request = new ChatRequest();
        request.setProvider(resolveProviderType(command));
        request.setModel(resolveActualModel(command.getApiModel()));

        List<ChatMessage> messages = new ArrayList<>();
        appendPlanningSystemMessages(messages);
        appendPlanningSkillMessages(messages, context);
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
        currentUserMessage.setContent(context.getOrCreateUserMessageContext().getCurrentMessage().getContent());
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

    private String buildPlanningContext(ConversationRuntimeContext context) {
        StringBuilder builder = new StringBuilder();
        AiChatMessageDTO currentUserMessage = context.getOrCreateUserMessageContext().getCurrentMessage();
        builder.append("是否新会话首轮：")
                .append(currentUserMessage != null && Integer.valueOf(1).equals(currentUserMessage.getSortNo()))
                .append('\n');
        String userMessageSummary = context.getOrCreateUserMessageContext().getSummary();
        if (StringUtils.hasText(userMessageSummary)) {
            builder.append("用户消息上下文汇总：").append('\n').append(userMessageSummary).append('\n');
        }
        List<String> resolvedTerms = context.get(ConversationRuntimeContextKeys.Skill.RESOLVED_BUSINESS_TERMS);
        if (!CollectionUtils.isEmpty(resolvedTerms)) {
            builder.append("业务术语补充：").append(resolvedTerms).append('\n');
        }
        Object normalizedTimeRange = context.get(ConversationRuntimeContextKeys.Skill.NORMALIZED_TIME_RANGE);
        if (normalizedTimeRange != null) {
            builder.append("时间范围补充：").append(normalizedTimeRange).append('\n');
        }
        return builder.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private void appendPlanningSkillMessages(List<ChatMessage> messages, ConversationRuntimeContext context) {
        List<PlanningContextMessage> contextMessages = context.get(ConversationRuntimeContextKeys.Planning.QUERY_PLANNING_CONTEXT_MESSAGES);
        if (CollectionUtils.isEmpty(contextMessages)) {
            return;
        }
        for (PlanningContextMessage item : contextMessages) {
            if (item == null || !StringUtils.hasText(item.getContent())) {
                continue;
            }
            ChatMessage message = new ChatMessage();
            message.setRole(item.getRole() == null ? MessageRole.SYSTEM : item.getRole());
            message.setContent(renderPlanningSkillMessage(item));
            messages.add(message);
        }
    }

    private String renderPlanningSkillMessage(PlanningContextMessage message) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(message.getSection())) {
            builder.append("【").append(message.getSection()).append("】").append('\n');
        }
        if (StringUtils.hasText(message.getSource())) {
            builder.append("来源：").append(message.getSource()).append('\n');
        }
        builder.append(message.getContent().trim());
        return builder.toString().trim();
    }

    private void appendIntentAnalysisContext(StringBuilder builder, IntentAnalysisBundle bundle) {
        if (StringUtils.hasText(bundle.getIntentType())) {
            builder.append("识别意图类型：").append(bundle.getIntentType()).append('\n');
        }
        if (!CollectionUtils.isEmpty(bundle.getIntentLabels())) {
            builder.append("意图标签：").append(bundle.getIntentLabels()).append('\n');
        }
        if (!CollectionUtils.isEmpty(bundle.getTerms())) {
            builder.append("关键词命中：").append(bundle.getTerms()).append('\n');
        }
        if (!CollectionUtils.isEmpty(bundle.getMetrics())) {
            builder.append("指标候选：").append(bundle.getMetrics()).append('\n');
        }
        if (!CollectionUtils.isEmpty(bundle.getDimensions())) {
            builder.append("维度候选：").append(bundle.getDimensions()).append('\n');
        }
        if (!CollectionUtils.isEmpty(bundle.getCandidateDatasets())) {
            builder.append("数据集候选：").append(bundle.getCandidateDatasets()).append('\n');
        }
        if (bundle.getTimeRange() != null && !bundle.getTimeRange().isEmpty()) {
            builder.append("时间范围候选：").append(bundle.getTimeRange()).append('\n');
        }
        if (!CollectionUtils.isEmpty(bundle.getRequiredContext())) {
            builder.append("预判所需上下文：").append(bundle.getRequiredContext()).append('\n');
        }
        if (!CollectionUtils.isEmpty(bundle.getRisks())) {
            builder.append("预判风险：").append(bundle.getRisks()).append('\n');
        }
        if (!CollectionUtils.isEmpty(bundle.getClarificationQuestions())) {
            builder.append("建议澄清问题：").append(bundle.getClarificationQuestions()).append('\n');
        }
        if (bundle.getConfidence() != null) {
            builder.append("意图分析置信度：").append(bundle.getConfidence()).append('\n');
        }
        if (!CollectionUtils.isEmpty(bundle.getEvidences())) {
            builder.append("技能证据摘要：").append(buildEvidenceSummary(bundle.getEvidences())).append('\n');
        }
    }

    private PlanningResult parsePlanningResult(String rawText, boolean requireSessionTitle) {
        String currentText = rawText;
        String validationError = null;
        int maxRetry = resolvePlanningStructureMaxRetry();
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                log.debug("parse planning result start, attempt={}, requireSessionTitle={}", attempt + 1, requireSessionTitle);
                PlanningResult result = objectMapper.readValue(cleanJson(currentText), PlanningResult.class);
                validatePlanningResult(result, requireSessionTitle);
                PlanningResult normalized = normalizePlanningResult(result);
                log.debug("parse planning result success, attempt={}, requireSessionTitle={}", attempt + 1, requireSessionTitle);
                return normalized;
            } catch (Exception ex) {
                validationError = ex.getMessage();
                log.warn("parse planning result failed, attempt={}, maxRetry={}, requireSessionTitle={}, error={}",
                        attempt + 1,
                        maxRetry,
                        requireSessionTitle,
                        validationError);
                if (attempt == maxRetry) {
                    throw invalidWorkflowOutput("planning result parse failed: " + validationError, ex);
                }
                currentText = retryPlanningWithFeedback(currentText, validationError, requireSessionTitle);
            }
        }
        throw invalidWorkflowOutput("planning result parse failed: " + validationError);
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
        log.info("retry query planning with feedback, requireSessionTitle={}, validationError={}", requireSessionTitle, validationError);
        ChatRequest retryRequest = new ChatRequest();
        retryRequest.setProvider(ProviderType.DASHSCOPE);
        retryRequest.setModel(resolveActualModel(null));

        List<ChatMessage> messages = new ArrayList<>();
        appendPlanningSystemMessages(messages);
        messages.add(buildMessage(MessageRole.ASSISTANT, defaultText(previousOutput)));
        messages.add(buildMessage(MessageRole.USER, """
                你上一次返回的 JSON 不合法，请严格修正后重新返回。
                校验错误：
                %s
                额外要求：
                - 只返回 JSON
                - 不允许代码块
                - title%s
                """.formatted(validationError, requireSessionTitle ? "必须非空" : "建议非空")));
        retryRequest.setMessages(messages);

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(1024);
        options.setTimeoutMs(30_000);
        retryRequest.setOptions(options);

        ChatResponse retryResponse = aiExecutionDomainService.chat(retryRequest);
        log.info("retry query planning finished, requestId={}, hasOutput={}",
                retryResponse == null ? null : retryResponse.getRequestId(),
                retryResponse != null && !CollectionUtils.isEmpty(retryResponse.getOutputs()));
        return extractAnswer(retryResponse);
    }

    private void appendPlanningSystemMessages(List<ChatMessage> messages) {
        messages.add(buildMessage(MessageRole.SYSTEM, PLANNING_TOPIC_PROMPT));
        messages.add(buildMessage(MessageRole.SYSTEM, PLANNING_STRUCTURE_PROMPT));
    }

    private void validatePlanningResult(PlanningResult result, boolean requireSessionTitle) {
        if (result == null) {
            throw invalidWorkflowOutput("result is null");
        }
        if (!StringUtils.hasText(result.getTitle()) && requireSessionTitle) {
            throw invalidWorkflowOutput("title is required for new conversation");
        }
        if (result.getSubject() == null) {
            throw invalidWorkflowOutput("subject is required");
        }
        if (!StringUtils.hasText(result.getSubject().getName())) {
            throw invalidWorkflowOutput("subject.name is required");
        }
        if (!StringUtils.hasText(result.getSubject().getValue())) {
            throw invalidWorkflowOutput("subject.value is required");
        }
        if (result.getSubject().getScore() == null) {
            throw invalidWorkflowOutput("subject.score is required");
        }
        if (result.getFilters() == null) {
            throw invalidWorkflowOutput("filters is required");
        }
        if (result.getIntent() == null) {
            throw invalidWorkflowOutput("intent is required");
        }
        if (!StringUtils.hasText(result.getIntent().getType())) {
            throw invalidWorkflowOutput("intent.type is required");
        }
        if (!StringUtils.hasText(result.getIntent().getName())) {
            throw invalidWorkflowOutput("intent.name is required");
        }
        if (result.getRender() == null) {
            throw invalidWorkflowOutput("render is required");
        }
        if (!StringUtils.hasText(result.getRender().getType())) {
            throw invalidWorkflowOutput("render.type is required");
        }
        if (!StringUtils.hasText(result.getRender().getName())) {
            throw invalidWorkflowOutput("render.name is required");
        }
        if (result.getAmbiguity() == null) {
            throw invalidWorkflowOutput("ambiguity is required");
        }
        if (result.getAmbiguity().getHasAmbiguity() == null) {
            throw invalidWorkflowOutput("ambiguity.hasAmbiguity is required");
        }
        if (result.getAmbiguity().getItems() == null) {
            throw invalidWorkflowOutput("ambiguity.items is required");
        }
        if (Boolean.TRUE.equals(result.getAmbiguity().getHasAmbiguity()) && result.getAmbiguity().getItems().isEmpty()) {
            throw invalidWorkflowOutput("ambiguity.items must not be empty when ambiguity.hasAmbiguity is true");
        }
        if (result.getExt() == null) {
            throw invalidWorkflowOutput("ext is required");
        }
    }

    private PlanningResult normalizePlanningResult(PlanningResult result) {
        result.setTitle(trimToNull(result.getTitle()));
        result.setSubject(defaultIfNull(result.getSubject(), new PlanningResult.Subject()));
        result.getSubject().setName(trimToNull(result.getSubject().getName()));
        result.getSubject().setValue(trimToNull(result.getSubject().getValue()));
        result.getSubject().setAliases(normalizeList(result.getSubject().getAliases()));
        result.getSubject().setRelations(normalizeRelations(result.getSubject().getRelations()));
        result.setFilters(normalizeFilters(result.getFilters()));
        result.setIntent(defaultIfNull(result.getIntent(), new PlanningResult.Intent()));
        result.getIntent().setType(trimToNull(result.getIntent().getType()));
        result.getIntent().setName(trimToNull(result.getIntent().getName()));
        result.getIntent().setAction(trimToNull(result.getIntent().getAction()));
        result.setRender(defaultIfNull(result.getRender(), new PlanningResult.Render()));
        result.getRender().setType(trimToNull(result.getRender().getType()));
        result.getRender().setName(trimToNull(result.getRender().getName()));
        result.setAmbiguity(defaultIfNull(result.getAmbiguity(), new PlanningResult.Ambiguity()));
        result.getAmbiguity().setItems(normalizeAmbiguityItems(result.getAmbiguity().getItems()));
        result.setExt(normalizeExt(result.getExt()));
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
        if (StringUtils.hasText(result.getTitle())) {
            joiner.add("规划标题：" + result.getTitle());
        }
        joiner.add("查询主体：" + buildSubjectSummary(result.getSubject()));
        if (!CollectionUtils.isEmpty(result.getFilters())) {
            joiner.add("查询条件：" + buildFilterSummary(result.getFilters()));
        }
        joiner.add("查询意图：" + buildIntentSummary(result.getIntent()));
        joiner.add("展示建议：" + buildRenderSummary(result.getRender()));
        if (result.getAmbiguity() != null) {
            joiner.add("是否有歧义：" + Boolean.TRUE.equals(result.getAmbiguity().getHasAmbiguity()));
            if (!CollectionUtils.isEmpty(result.getAmbiguity().getItems())) {
                joiner.add("待确认项：" + buildAmbiguitySummary(result.getAmbiguity().getItems()));
            }
        }
        String extSummary = buildExtSummary(result.getExt());
        if (StringUtils.hasText(extSummary)) {
            joiner.add("扩展信息：" + extSummary);
        }
        return joiner.toString();
    }

    private String buildIntentAnalysisSummary(IntentAnalysisBundle bundle) {
        if (bundle == null) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("\n");
        if (StringUtils.hasText(bundle.getIntentType())) {
            joiner.add("意图类型：" + bundle.getIntentType());
        }
        if (!CollectionUtils.isEmpty(bundle.getIntentLabels())) {
            joiner.add("意图标签：" + String.join("；", bundle.getIntentLabels()));
        }
        if (!CollectionUtils.isEmpty(bundle.getTerms())) {
            joiner.add("关键词证据：" + String.join("；", bundle.getTerms()));
        }
        if (!CollectionUtils.isEmpty(bundle.getMetrics())) {
            joiner.add("指标候选：" + String.join("；", bundle.getMetrics()));
        }
        if (!CollectionUtils.isEmpty(bundle.getDimensions())) {
            joiner.add("维度候选：" + String.join("；", bundle.getDimensions()));
        }
        if (!CollectionUtils.isEmpty(bundle.getCandidateDatasets())) {
            joiner.add("数据集候选：" + String.join("；", bundle.getCandidateDatasets()));
        }
        if (bundle.getTimeRange() != null && !bundle.getTimeRange().isEmpty()) {
            joiner.add("时间范围候选：" + bundle.getTimeRange());
        }
        if (!CollectionUtils.isEmpty(bundle.getRequiredContext())) {
            joiner.add("所需上下文：" + String.join("；", bundle.getRequiredContext()));
        }
        if (!CollectionUtils.isEmpty(bundle.getRisks())) {
            joiner.add("预判风险：" + String.join("；", bundle.getRisks()));
        }
        if (!CollectionUtils.isEmpty(bundle.getClarificationQuestions())) {
            joiner.add("建议澄清：" + String.join("；", bundle.getClarificationQuestions()));
        }
        if (bundle.getConfidence() != null) {
            joiner.add("置信度：" + bundle.getConfidence());
        }
        return joiner.toString();
    }

    private String buildEvidenceSummary(List<IntentEvidence> evidences) {
        List<String> summaries = new ArrayList<>();
        for (IntentEvidence evidence : evidences) {
            if (evidence == null || !StringUtils.hasText(evidence.getSource())) {
                continue;
            }
            StringJoiner joiner = new StringJoiner(", ");
            joiner.add("source=" + evidence.getSource());
            if (StringUtils.hasText(evidence.getIntentType())) {
                joiner.add("intentType=" + evidence.getIntentType());
            }
            if (evidence.getScore() != null) {
                joiner.add("score=" + evidence.getScore());
            }
            if (StringUtils.hasText(evidence.getSummary())) {
                joiner.add("summary=" + evidence.getSummary());
            }
            summaries.add("{" + joiner + "}");
        }
        return summaries.toString();
    }

    private <T> T defaultIfNull(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private List<PlanningResult.RelationItem> normalizeRelations(List<PlanningResult.RelationItem> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .peek(item -> {
                    item.setName(trimToNull(item.getName()));
                    item.setValues(normalizeList(item.getValues()));
                    item.setAliases(normalizeList(item.getAliases()));
                })
                .filter(item -> StringUtils.hasText(item.getName())
                        || !CollectionUtils.isEmpty(item.getValues())
                        || !CollectionUtils.isEmpty(item.getAliases()))
                .toList();
    }

    private List<PlanningResult.FilterItem> normalizeFilters(List<PlanningResult.FilterItem> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .peek(item -> {
                    item.setKey(trimToNull(item.getKey()));
                    item.setValue(trimToNull(item.getValue()));
                    item.setModel(trimToNull(item.getModel()));
                    item.setSource(trimToNull(item.getSource()));
                })
                .filter(item -> StringUtils.hasText(item.getKey())
                        || StringUtils.hasText(item.getValue())
                        || StringUtils.hasText(item.getModel()))
                .toList();
    }

    private List<PlanningResult.AmbiguityItem> normalizeAmbiguityItems(List<PlanningResult.AmbiguityItem> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .peek(item -> {
                    item.setType(trimToNull(item.getType()));
                    item.setQuestion(trimToNull(item.getQuestion()));
                    item.setSuggestion(trimToNull(item.getSuggestion()));
                })
                .filter(item -> StringUtils.hasText(item.getType())
                        || StringUtils.hasText(item.getQuestion())
                        || StringUtils.hasText(item.getSuggestion()))
                .toList();
    }

    private Map<String, Object> normalizeExt(Map<String, Object> ext) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (ext == null) {
            normalized.put(PlanningExtKeys.METRICS, List.of());
            normalized.put(PlanningExtKeys.DIMENSIONS, List.of());
            normalized.put(PlanningExtKeys.SORT, List.of());
            normalized.put(PlanningExtKeys.STATISTICAL_CALIBER, List.of());
            normalized.put(PlanningExtKeys.SEMANTIC_TERMS, List.of());
            return normalized;
        }
        normalized.putAll(ext);
        normalizeExtList(normalized, PlanningExtKeys.METRICS);
        normalizeExtList(normalized, PlanningExtKeys.DIMENSIONS);
        normalizeExtList(normalized, PlanningExtKeys.SORT);
        normalizeExtList(normalized, PlanningExtKeys.STATISTICAL_CALIBER);
        normalizeExtList(normalized, PlanningExtKeys.SEMANTIC_TERMS);
        return normalized;
    }

    private void normalizeExtList(Map<String, Object> ext, String key) {
        Object value = ext.get(key);
        if (value == null) {
            ext.put(key, List.of());
            return;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = list.stream()
                    .filter(Objects::nonNull)
                    .map(item -> item instanceof String str ? str.trim() : item)
                    .filter(item -> !(item instanceof String str) || StringUtils.hasText(str))
                    .toList();
            ext.put(key, normalized);
            return;
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            ext.put(key, StringUtils.hasText(trimmed) ? List.of(trimmed) : List.of());
        }
    }

    private String buildSubjectSummary(PlanningResult.Subject subject) {
        if (subject == null) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(" / ");
        if (StringUtils.hasText(subject.getName())) {
            joiner.add(subject.getName());
        }
        if (StringUtils.hasText(subject.getValue())) {
            joiner.add(subject.getValue());
        }
        if (!CollectionUtils.isEmpty(subject.getAliases())) {
            joiner.add("aliases=" + subject.getAliases());
        }
        if (subject.getScore() != null) {
            joiner.add("score=" + subject.getScore());
        }
        if (!CollectionUtils.isEmpty(subject.getRelations())) {
            List<String> relationParts = subject.getRelations().stream()
                    .filter(Objects::nonNull)
                    .map(this::buildRelationSummary)
                    .filter(StringUtils::hasText)
                    .toList();
            if (!relationParts.isEmpty()) {
                joiner.add("relations=" + relationParts);
            }
        }
        return joiner.toString();
    }

    private String buildRelationSummary(PlanningResult.RelationItem relation) {
        StringJoiner joiner = new StringJoiner(" / ");
        if (StringUtils.hasText(relation.getName())) {
            joiner.add(relation.getName());
        }
        if (!CollectionUtils.isEmpty(relation.getValues())) {
            joiner.add("values=" + relation.getValues());
        }
        if (!CollectionUtils.isEmpty(relation.getAliases())) {
            joiner.add("aliases=" + relation.getAliases());
        }
        if (relation.getScore() != null) {
            joiner.add("score=" + relation.getScore());
        }
        return joiner.toString();
    }

    private String buildFilterSummary(List<PlanningResult.FilterItem> filters) {
        List<String> parts = new ArrayList<>();
        for (PlanningResult.FilterItem item : filters) {
            if (item == null) {
                continue;
            }
            StringJoiner joiner = new StringJoiner(" ");
            if (StringUtils.hasText(item.getKey())) {
                joiner.add(item.getKey());
            }
            if (StringUtils.hasText(item.getValue())) {
                joiner.add(item.getValue());
            }
            if (StringUtils.hasText(item.getModel())) {
                joiner.add("model=" + item.getModel());
            }
            if (StringUtils.hasText(item.getSource())) {
                joiner.add("source=" + item.getSource());
            }
            if (item.getScore() != null) {
                joiner.add("score=" + item.getScore());
            }
            String text = joiner.toString().trim();
            if (StringUtils.hasText(text)) {
                parts.add(text);
            }
        }
        return String.join("；", parts);
    }

    private String buildIntentSummary(PlanningResult.Intent intent) {
        if (intent == null) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(" / ");
        if (StringUtils.hasText(intent.getType())) {
            joiner.add(intent.getType());
        }
        if (StringUtils.hasText(intent.getName())) {
            joiner.add(intent.getName());
        }
        if (StringUtils.hasText(intent.getAction())) {
            joiner.add(intent.getAction());
        }
        return joiner.toString();
    }

    private String buildRenderSummary(PlanningResult.Render render) {
        if (render == null) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(" / ");
        if (StringUtils.hasText(render.getType())) {
            joiner.add(render.getType());
        }
        if (StringUtils.hasText(render.getName())) {
            joiner.add(render.getName());
        }
        return joiner.toString();
    }

    private String buildAmbiguitySummary(List<PlanningResult.AmbiguityItem> items) {
        List<String> parts = new ArrayList<>();
        for (PlanningResult.AmbiguityItem item : items) {
            if (item == null) {
                continue;
            }
            StringJoiner joiner = new StringJoiner(" / ");
            if (StringUtils.hasText(item.getType())) {
                joiner.add(item.getType());
            }
            if (StringUtils.hasText(item.getQuestion())) {
                joiner.add(item.getQuestion());
            }
            if (item.getImportance() != null) {
                joiner.add("importance=" + item.getImportance());
            }
            if (StringUtils.hasText(item.getSuggestion())) {
                joiner.add("suggestion=" + item.getSuggestion());
            }
            String text = joiner.toString().trim();
            if (StringUtils.hasText(text)) {
                parts.add(text);
            }
        }
        return String.join("；", parts);
    }

    private String buildExtSummary(Map<String, Object> ext) {
        if (ext == null || ext.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : ext.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join("；", parts);
    }

    private ChatMessage buildMessage(MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private String cleanJson(String text) {
        if (!StringUtils.hasText(text)) {
            throw invalidWorkflowOutput("planning output is empty");
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

    private ProviderType resolveProviderType(ConversationQueryCommand command) {
        String apiModel = command == null ? null : command.getApiModel();
        if (StringUtils.hasText(apiModel) && apiModel.toLowerCase(Locale.ROOT).contains("qwen")) {
            return ProviderType.DASHSCOPE;
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

    private int resolveMaxTokens(String apiModel) {
        return 1024;
    }

    private String resolveActualModel(String apiModel) {
        if (StringUtils.hasText(apiModel)) {
            return apiModel.trim();
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

    private BizException invalidWorkflowOutput(String message) {
        return BizException.of(AiChatBizCodeConstant.INVALID_WORKFLOW_OUTPUT, message);
    }

    private BizException invalidWorkflowOutput(String message, Throwable cause) {
        return BizException.of(AiChatBizCodeConstant.INVALID_WORKFLOW_OUTPUT, message + ": " + cause.getMessage());
    }

}
