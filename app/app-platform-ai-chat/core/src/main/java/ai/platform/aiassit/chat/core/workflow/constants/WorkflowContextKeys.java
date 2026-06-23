package ai.platform.aiassit.chat.core.workflow.constants;

import ai.platform.aiassist.service.ai.api.dto.HybridSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.IntentAnalyzeResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.planning.contract.IntentAnalysisBundle;
import ai.platform.aiassit.chat.core.workflow.planning.contract.IntentEvidence;
import ai.platform.aiassit.chat.core.workflow.planning.contract.PlanningContextMessage;
import ai.platform.aiassit.chat.core.workflow.planning.contract.PlanningResult;
import ai.platform.aiassit.chat.core.workflow.sql.contract.SqlPreGenerateResult;

import java.util.List;
import java.util.Map;

/**
 * {@link WorkflowContext#put(String, Object)} / {@link WorkflowContext#get(String)} 动态上下文键常量。
 *
 * <p>按通用域、规划域、技能域、Capability 域和节点产物域分组，避免散落字符串。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/23
 */
public interface WorkflowContextKeys {

    /**
     * 通用上下文键。
     */
    interface Common {

        /**
         * 工作流统一错误消息。
         *
         * @see String
         */
        String ERROR = "error";

    }

    /**
     * 聊天消息域上下文键。
     */
    interface ChatMessage {

        /**
         * 会话摘要技能失败消息。
         *
         * @see String
         */
        String SUMMARY_ERROR = "chatMessageSummaryError";

    }

    /**
     * 查询规划域上下文键。
     */
    interface Planning {

        /**
         * 聚合后的意图分析结果。
         *
         * @see IntentAnalysisBundle
         */
        String INTENT_ANALYSIS_BUNDLE = "intentAnalysisBundle";

        /**
         * 查询规划证据列表。
         *
         * @see IntentEvidence
         */
        String QUERY_PLANNING_EVIDENCES = "queryPlanningEvidences";

        /**
         * 查询规划上下文消息列表。
         *
         * @see PlanningContextMessage
         */
        String QUERY_PLANNING_CONTEXT_MESSAGES = "queryPlanningContextMessages";

        /**
         * 规划摘要文本。
         *
         * @see String
         */
        String QUERY_PLAN = "queryPlan";

        /**
         * 结构化规划结果。
         *
         * @see PlanningResult
         */
        String QUERY_PLAN_RESULT = "queryPlanResult";

        /**
         * 规划分析摘要。
         *
         * @see String
         */
        String QUERY_PLANNING_SUMMARY = "queryPlanningSummary";

        /**
         * 规划请求 ID。
         *
         * @see String
         */
        String PLANNING_REQUEST_ID = "planningRequestId";

        /**
         * 意图识别响应。
         *
         * @see IntentAnalyzeResponse
         */
        String INTENT_ANALYZE_RESPONSE = "intentAnalyzeResponse";

        /**
         * 关键词检索原始响应。
         *
         * @see HybridSearchResponse
         */
        String KEYWORD_HYBRID_SEARCH_RESPONSE = "keywordHybridSearchResponse";

        /**
         * 关键词检索摘要。
         *
         * @see String
         */
        String KEYWORD_HYBRID_SEARCH_SUMMARY = "keywordHybridSearchSummary";

        /**
         * 向量检索原始响应。
         *
         * @see HybridSearchResponse
         */
        String VECTOR_HYBRID_SEARCH_RESPONSE = "vectorHybridSearchResponse";

        /**
         * 向量检索摘要。
         *
         * @see String
         */
        String VECTOR_HYBRID_SEARCH_SUMMARY = "vectorHybridSearchSummary";

    }

    /**
     * 技能域上下文键。
     */
    interface Skill {

        /**
         * 解析后的业务术语列表。
         *
         * @see List
         */
        String RESOLVED_BUSINESS_TERMS = "resolvedBusinessTerms";

        /**
         * 标准化时间范围。
         *
         * @see Map
         */
        String NORMALIZED_TIME_RANGE = "normalizedTimeRange";

        /**
         * SQL 生成规范。
         *
         * @see Map
         */
        String SQL_GENERATION_POLICY = "sqlGenerationPolicy";

        /**
         * 解析后的用户偏好。
         *
         * @see Map
         */
        String RESOLVED_USER_PREFERENCES = "resolvedUserPreferences";

    }

    /**
     * Prompt Capability 域上下文键。
     */
    interface Capability {

        /**
         * 知识库检索原始响应。
         *
         * @see KbSearchResponse
         */
        String KNOWLEDGE_SEARCH_RESPONSE = "knowledgeSearchResponse";

        /**
         * 知识库上下文文本。
         *
         * @see String
         */
        String KNOWLEDGE_RESULT = "knowledgeResult";

    }

    /**
     * SQL 生成域上下文键。
     */
    interface SqlGenerate {

        /**
         * SQL 预生成结构化结果。
         *
         * @see SqlPreGenerateResult
         */
        String PRE_GENERATE_RESULT = "sqlPreGenerateResult";

        /**
         * 当前候选 SQL。
         *
         * @see String
         */
        String GENERATED_SQL = "generatedSql";

        /**
         * SQL 生成请求 ID。
         *
         * @see String
         */
        String REQUEST_ID = "sqlGenerateRequestId";

        /**
         * SQL 重试反馈。
         *
         * @see String
         */
        String FEEDBACK = "sqlGenerationFeedback";

    }

    /**
     * SQL 校验域上下文键。
     */
    interface SqlValidate {

        /**
         * SQL 校验错误。
         *
         * @see String
         */
        String VALIDATION_ERROR = "sqlValidationError";

    }

    /**
     * SQL 执行域上下文键。
     */
    interface SqlExecute {

        /**
         * SQL 执行结果。
         *
         * @see Object
         */
        String RESULT = "sqlExecutionResult";

    }

    /**
     * 渲染域上下文键。
     */
    interface Render {

        /**
         * 最终回答文本。
         *
         * @see String
         */
        String RENDERED_ANSWER = "renderedAnswer";

    }
}
