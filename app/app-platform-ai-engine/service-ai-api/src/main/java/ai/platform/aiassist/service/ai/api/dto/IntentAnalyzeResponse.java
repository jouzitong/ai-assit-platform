package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class IntentAnalyzeResponse implements Serializable {

    /**
     * 预留业务类型字段。
     *
     * <p>当前阶段仅作为 AI 给出的候选业务域参考，不应直接作为最终业务判断或路由依据；
     * 后续真正消费该字段时，必须结合核心业务定义重新核对。</p>
     */
    private String businessType;

    /**
     * 请求唯一标识，用于链路追踪和日志排查。
     */
    private String requestId;

    /**
     * 本次意图分析使用的模型名称。
     */
    private String model;

    /**
     * 意图类型。
     *
     * <p>当前约定仅支持：
     * SIMPLE_CHAT：普通聊天/解释/建议类诉求；
     * QUERY_RENDER：需要进入查询、分析、渲染链路的数据类诉求。</p>
     */
    private String intentType;

    /**
     * 改写后的用户问题，用于消除口语化表达或补全上下文。
     */
    private String rewrittenQuery;

    /**
     * 意图分析摘要，描述用户核心诉求。
     */
    private String summary;

    /**
     * 意图标签列表，用于后续流程路由、能力匹配或分类检索。
     */
    private List<String> intentLabels = new ArrayList<>();

    /**
     * 用户关注的指标列表，例如：销售额、利润、人数、完成率等。
     */
    private List<String> metrics = new ArrayList<>();

    /**
     * 用户关注的维度列表，例如：部门、区域、时间、人员类型等。
     */
    private List<String> dimensions = new ArrayList<>();

    /**
     * 候选数据集列表，用于标识本次查询可能涉及的数据表、主题域或数据模型。
     */
    private List<String> candidateDatasets = new ArrayList<>();

    /**
     * 仍然需要补充的上下文信息，例如：时间范围、统计口径、业务对象等。
     */
    private List<String> requiredContext = new ArrayList<>();

    /**
     * 意图分析过程中识别出的风险点，例如：口径不明确、权限风险、数据源不确定等。
     */
    private List<String> risks = new ArrayList<>();

    /**
     * 是否需要向用户追问澄清。
     */
    private Boolean clarificationNeeded = Boolean.FALSE;

    /**
     * 需要向用户追问的问题列表。
     */
    private List<String> clarificationQuestions = new ArrayList<>();

    /**
     * 其他重要信息列表，用于补充后续规划和执行时需要关注的关键点。
     */
    private List<String> importantInfos = new ArrayList<>();

    /**
     * 时间范围信息，支持存放开始时间、结束时间、相对时间描述等结构化内容。
     */
    private Map<String, Object> timeRange = new HashMap<>();

    /**
     * AI 原始输出内容，用于调试、审计或异常排查。
     */
    private String rawOutput;
}
