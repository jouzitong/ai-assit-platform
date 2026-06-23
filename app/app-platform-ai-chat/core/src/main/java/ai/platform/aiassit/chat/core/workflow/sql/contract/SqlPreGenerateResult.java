package ai.platform.aiassit.chat.core.workflow.sql.contract;

import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 预生成结果。
 *
 * <p>用于承接 SQL 节点在真正生成 SQL 前识别到的主表、关联表、过滤条件与问题信息。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/23
 */
@Data
public class SqlPreGenerateResult {

    /**
     * 原始知识库检索结果，保留完整表结构内容，供后续节点继续使用。
     */
    private KbSearchResponse knowledgeSearchResponse;

    /**
     * 主表候选结构。
     */
    private MainTableStruct mainTable = new MainTableStruct();

    /**
     * 关联表候选结构。
     */
    private List<RelationTableStruct> relationTables = new ArrayList<>();

    /**
     * 过滤条件结构。
     */
    private List<FilterConditionStruct> filters = new ArrayList<>();

    /**
     * 问题与建议。
     */
    private List<ProblemStruct> problems = new ArrayList<>();

    /**
     * 整体可信度。
     */
    private Double confidence;

    @Data
    public static class MainTableStruct {

        /**
         * 数据库真实表名或当前推断表名。
         */
        private String tableName;

        /**
         * 表说明。
         */
        private String tableComment;

        /**
         * 可信度。
         */
        private Double confidence;

        /**
         * 命中的知识库引用。
         */
        private List<KnowledgeHitRef> knowledgeHits = new ArrayList<>();
    }

    @Data
    public static class RelationTableStruct {

        /**
         * 数据库真实表名或当前推断表名。
         */
        private String tableName;

        /**
         * 表说明。
         */
        private String tableComment;

        /**
         * 关联方式。
         */
        private String relationType;

        /**
         * 关联说明。
         */
        private String relationComment;

        /**
         * 可信度。
         */
        private Double confidence;

        /**
         * 命中的知识库引用。
         */
        private List<KnowledgeHitRef> knowledgeHits = new ArrayList<>();
    }

    @Data
    public static class FilterConditionStruct {

        /**
         * 过滤对象表名。
         */
        private String tableName;

        /**
         * 过滤字段名。
         */
        private String fieldName;

        /**
         * 过滤操作符。
         */
        private String operator;

        /**
         * 过滤值。
         */
        private String value;

        /**
         * 条件说明。
         */
        private String conditionComment;

        /**
         * 可信度。
         */
        private Double confidence;
    }

    @Data
    public static class ProblemStruct {

        /**
         * 问题类型。
         */
        private String type;

        /**
         * 问题描述。
         */
        private String message;

        /**
         * 建议方案。
         */
        private String suggestion;

        /**
         * 是否阻断后续处理。
         */
        private Boolean blocking;

        /**
         * 可信度。
         */
        private Double confidence;
    }

    @Data
    public static class KnowledgeHitRef {

        /**
         * 命中文档 ID。
         */
        private String documentId;

        /**
         * 命中分数。
         */
        private Double score;

        /**
         * 归类原因。
         */
        private String reason;
    }
}
