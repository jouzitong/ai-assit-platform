package ai.platform.aiassit.conversation.workflow.planning.contract;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个意图分析技能输出的证据。
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Data
public class IntentEvidence {

    /**
     * 证据来源。
     * <p>
     * 通常用于标识该证据由哪个意图分析技能、规则、模型或外部检索结果产生。
     * </p>
     */
    private String source;

    /**
     * 证据摘要。
     * <p>
     * 用于简要描述当前技能对用户问题的理解、判断依据或分析结论。
     * </p>
     */
    private String summary;

    /**
     * 证据置信分数。
     * <p>
     * 用于表示当前证据对意图判断的可信程度，分数越高代表该证据越可靠。
     * </p>
     */
    private Double score;

    /**
     * 意图类型。
     * <p>
     * 用于标识当前证据识别出的核心意图分类，例如查询、统计、对比、趋势分析等。
     * </p>
     */
    private String intentType;

    /**
     * 意图标签列表。
     * <p>
     * 用于保存更细粒度的意图标签，便于后续流程根据标签进行路由、排序或补充分析。
     * </p>
     */
    private List<String> intentLabels = new ArrayList<>();

    /**
     * 关键词列表。
     * <p>
     * 用于保存从用户问题中提取出的核心业务词、实体词或检索词。
     * </p>
     */
    private List<String> terms = new ArrayList<>();

    /**
     * 指标列表。
     * <p>
     * 用于保存用户问题中涉及的统计指标、业务指标或计算字段。
     * </p>
     */
    private List<String> metrics = new ArrayList<>();

    /**
     * 维度列表。
     * <p>
     * 用于保存用户问题中涉及的分析维度，例如时间、部门、区域、人员、产品等。
     * </p>
     */
    private List<String> dimensions = new ArrayList<>();

    /**
     * 候选数据集列表。
     * <p>
     * 用于保存当前证据推断出的可能相关数据源、数据表、主题域或数据集标识。
     * </p>
     */
    private List<String> candidateDatasets = new ArrayList<>();

    /**
     * 必要上下文列表。
     * <p>
     * 用于保存完成本次意图分析或后续 SQL 生成所需要补充的上下文信息。
     * </p>
     */
    private List<String> requiredContext = new ArrayList<>();

    /**
     * 风险提示列表。
     * <p>
     * 用于保存当前意图分析中发现的潜在问题，例如字段歧义、时间范围缺失、指标口径不明确等。
     * </p>
     */
    private List<String> risks = new ArrayList<>();

    /**
     * 澄清问题列表。
     * <p>
     * 当用户问题信息不足或存在歧义时，用于保存需要向用户追问的问题。
     * </p>
     */
    private List<String> clarificationQuestions = new ArrayList<>();

    /**
     * 是否需要澄清。
     * <p>
     * 用于标识当前问题是否需要用户补充信息后才能继续执行后续流程。
     * </p>
     */
    private Boolean clarificationNeeded;

    /**
     * 改写后的用户问题。
     * <p>
     * 用于保存经过意图分析技能规范化、补全或改写后的查询描述，便于后续节点使用。
     * </p>
     */
    private String rewrittenQuery;

    /**
     * 时间范围信息。
     * <p>
     * 用于保存从用户问题中解析出的时间条件，例如开始时间、结束时间、相对时间、时间粒度等。
     * </p>
     */
    private Map<String, Object> timeRange = new LinkedHashMap<>();

    /**
     * 扩展属性。
     * <p>
     * 用于保存当前证据中的其他结构化信息，方便不同技能按需扩展。
     * </p>
     */
    private Map<String, Object> attributes = new LinkedHashMap<>();
}
