package ai.platform.aiassit.conversation.workflow.planning.contract;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询规划结果。
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Data
public class PlanningResult {

    /**
     * 规划标题，首轮会话时会用于刷新会话标题。
     */
    private String title;

    /**
     * 查询主体。
     */
    private Subject subject = new Subject();

    /**
     * 查询条件。
     */
    private List<FilterItem> filters = new ArrayList<>();

    /**
     * 查询意图。
     */
    private Intent intent = new Intent();

    /**
     * 展示建议。
     */
    private Render render = new Render();

    /**
     * 模糊点与待确认项。
     */
    private Ambiguity ambiguity = new Ambiguity();

    /**
     * 扩展字段。
     */
    private Map<String, Object> ext = new LinkedHashMap<>();

    @Data
    public static class Subject {

        /**
         * 主体名称，例如：员工、部门、订单、销售业绩。
         */
        private String name;

        /**
         * 主体原始值，通常来自用户输入中的核心查询对象。
         */
        private String value;

        /**
         * 主体别名，用于保存与主体含义相近的业务词、简称或同义词。
         */
        private List<String> aliases = new ArrayList<>();

        /**
         * 主体识别可信度，数值越高表示越可信。
         */
        private Double score;

        /**
         * 主体关联对象，例如：员工关联部门、订单关联客户。
         */
        private List<RelationItem> relations = new ArrayList<>();
    }

    @Data
    public static class RelationItem {

        /**
         * 关联对象名称，例如：部门、客户、商品。
         */
        private String name;

        /**
         * 关联对象值列表，用于描述用户明确提到的关联对象取值。
         */
        private List<String> values = new ArrayList<>();

        /**
         * 关联对象别名，用于保存关联对象的简称、同义词或业务近义词。
         */
        private List<String> aliases = new ArrayList<>();

        /**
         * 关联对象识别可信度，数值越高表示越可信。
         */
        private Double score;
    }

    @Data
    public static class FilterItem {

        /**
         * 条件 key，用于描述当前过滤条件的核心语义标识。
         */
        private String key;

        /**
         * 条件值，保留用户输入中的核心过滤内容。
         */
        private String value;

        /**
         * 可能的模型说明，用于描述该条件可能关联的模型或表信息。
         */
        private String model;

        /**
         * 条件判断依据来源，例如：用户原话、历史上下文、规则推断、知识召回。
         */
        private String source;

        /**
         * 当前条件识别置信度，数值越高表示越可信。
         */
        private Double score;
    }

    @Data
    public static class Intent {

        /**
         * 意图类型，支持多个值，多个值之间使用英文逗号分隔。
         * 例如：query,count、aggregate,compare、trend,detail。
         */
        private String type;

        /**
         * 意图名称，用于展示或描述当前用户想完成的业务目标。
         */
        private String name;

        /**
         * 建议执行动作，支持多个值，多个值之间使用英文逗号分隔。
         * 例如：list,get、count,sum、group,chart。
         */
        private String action;

        /**
         * 查询意图识别置信度，数值越高表示越可信。
         */
        private Double score;
    }

    @Data
    public static class Render {

        /**
         * 展示类型，支持多个值，多个值之间使用英文逗号分隔。
         * 例如：table,card、chart,text、profile,dashboard。
         */
        private String type;

        /**
         * 展示名称，用于描述推荐给前端的展示方式。
         */
        private String name;

        /**
         * 展示建议置信度，数值越高表示越适合当前查询结果。
         */
        private Double score;
    }

    @Data
    public static class Ambiguity {

        /**
         * 是否存在模糊点或待确认项。
         */
        private Boolean hasAmbiguity = Boolean.FALSE;

        /**
         * 模糊点明细列表，用于记录需要用户确认或系统补全的问题。
         */
        private List<AmbiguityItem> items = new ArrayList<>();
    }

    @Data
    public static class AmbiguityItem {

        /**
         * 模糊点类型，例如：subject、filter、intent、render、permission。
         */
        private String type;

        /**
         * 面向用户的确认问题，用于引导用户补充信息。
         */
        private String question;

        /**
         * 重要程度，使用整形分值表示，数值越大越重要。
         */
        private Integer importance;

        /**
         * 建议补充内容说明，用于提示用户补充什么信息更有助于后续执行。
         */
        private String suggestion;
    }

}
