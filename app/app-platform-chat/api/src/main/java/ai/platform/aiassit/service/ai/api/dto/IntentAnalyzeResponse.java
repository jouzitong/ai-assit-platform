package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class IntentAnalyzeResponse implements Serializable {

    /**
     * 请求唯一标识，用于链路追踪和日志排查。
     */
    private String requestId;

    /**
     * 本次意图分析使用的模型名称。
     */
    private String model;

    /**
     * 意图类型，仅允许 SIMPLE_CHAT 或 QUERY_RENDER。
     */
    private String intentType;

    /**
     * 改写后的用户问题，用于纠正错别字、消除口语化表达或补全上下文。
     */
    private String rewrittenQuery;

    /**
     * 意图分析摘要，描述用户核心诉求。
     */
    private String summary;

    /**
     * 推荐的会话标题。
     */
    private String sessionTitle;

    /**
     * 是否发生了错别字或明显文本纠正。
     */
    private Boolean typoCorrected = Boolean.FALSE;

    /**
     * 文本纠正明细，例如：“GMv -> GMV”。
     */
    private List<String> corrections = new ArrayList<>();

    /**
     * 基础意图分析风险信息。
     */
    private RiskInfo risk = new RiskInfo();

    /**
     * 本轮整体判断评分，建议取值 0~1。
     */
    private Double score;

    /**
     * 历史意图失效情况摘要。
     */
    private String invalidIntentSummary;

    /**
     * 历史意图失效明细。
     */
    private List<InvalidIntentItem> invalidIntents = new ArrayList<>();

    /**
     * 是否需要向用户追问澄清。
     */
    private Boolean clarificationNeeded = Boolean.FALSE;

    /**
     * 建议向用户追问的核心问题。
     */
    private String clarificationQuestion;

    /**
     * AI 原始输出内容，用于调试、审计或异常排查。
     */
    private String rawOutput;

    @Data
    public static class RiskInfo implements Serializable {

        /**
         * 风险等级，例如 LOW / MEDIUM / HIGH。
         */
        private String level;

        /**
         * 风险总结描述。
         */
        private String summary;

        /**
         * 风险明细项。
         */
        private List<RiskItem> items = new ArrayList<>();
    }

    @Data
    public static class RiskItem implements Serializable {

        /**
         * 风险类型，例如 TYPO / MISSING_CONTEXT / CONFLICT_WITH_HISTORY。
         */
        private String type;

        /**
         * 风险描述。
         */
        private String description;

        /**
         * 风险依据。
         */
        private String evidence;

        /**
         * 该风险判断评分，建议取值 0~1。
         */
        private Double score;
    }

    @Data
    public static class InvalidIntentItem implements Serializable {

        /**
         * 已失效的旧意图或旧观点。
         */
        private String content;

        /**
         * 失效原因说明。
         */
        private String reason;

        /**
         * 失效依据内容。
         */
        private String evidence;

        /**
         * 该失效判断评分，建议取值 0~1。
         */
        private Double score;
    }
}
