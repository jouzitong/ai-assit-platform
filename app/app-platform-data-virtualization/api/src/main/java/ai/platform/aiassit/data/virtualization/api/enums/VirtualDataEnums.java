package ai.platform.aiassit.data.virtualization.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/** 虚拟数据模块的稳定业务枚举。 */
public final class VirtualDataEnums {

    private VirtualDataEnums() {
    }

    @Getter
    public enum CatalogStatus implements IEnum {
        DRAFT(0, "草稿"), PUBLISHED(1, "已发布"), DISABLED(2, "已停用");
        @JsonValue private final int code;
        private final String name;
        CatalogStatus(int code, String name) { this.code = code; this.name = name; }
    }

    @Getter
    public enum LogicalType implements IEnum {
        STRING(0, "字符串"), BOOLEAN(1, "布尔"), INTEGER(2, "整数"), LONG(3, "长整数"),
        DECIMAL(4, "十进制"), DATE(5, "日期"), TIMESTAMP(6, "时间戳"), JSON(7, "JSON"),
        BINARY(8, "二进制");
        @JsonValue private final int code;
        private final String name;
        LogicalType(int code, String name) { this.code = code; this.name = name; }
    }

    @Getter
    public enum BindingRole implements IEnum {
        PRIMARY(0, "主绑定"), REPLICA(1, "副本");
        @JsonValue private final int code;
        private final String name;
        BindingRole(int code, String name) { this.code = code; this.name = name; }
    }

    @Getter
    public enum TransformMode implements IEnum {
        READ_ONLY(0, "只读"), WRITE_ONLY(1, "只写"), BIDIRECTIONAL(2, "双向");
        @JsonValue private final int code;
        private final String name;
        TransformMode(int code, String name) { this.code = code; this.name = name; }
        public boolean readable() { return this != WRITE_ONLY; }
        public boolean writable() { return this != READ_ONLY; }
    }

    @Getter
    public enum FieldSide implements IEnum {
        PHYSICAL(0, "物理字段"), VIRTUAL(1, "虚拟字段");
        @JsonValue private final int code;
        private final String name;
        FieldSide(int code, String name) { this.code = code; this.name = name; }
    }

    @Getter
    public enum RoutingStrategy implements IEnum {
        SINGLE(0, "单绑定"), HASH(1, "哈希"), RANGE(2, "范围"), LIST(3, "列表");
        @JsonValue private final int code;
        private final String name;
        RoutingStrategy(int code, String name) { this.code = code; this.name = name; }
    }

    @Getter
    public enum ConsistencyLevel implements IEnum {
        STRONG(0, "强一致"), EVENTUAL(1, "最终一致"), READ_YOUR_WRITES(2, "读己之写");
        @JsonValue private final int code;
        private final String name;
        ConsistencyLevel(int code, String name) { this.code = code; this.name = name; }
    }

    @Getter
    public enum QueryType implements IEnum {
        LIST(0, "列表"), GET(1, "单条"), COUNT(2, "计数"), AGGREGATE(3, "聚合");
        @JsonValue private final int code;
        private final String name;
        QueryType(int code, String name) { this.code = code; this.name = name; }
    }

    @Getter
    public enum FilterType implements IEnum {
        AND(0, "并且"), OR(1, "或者"), NOT(2, "非"), PREDICATE(3, "谓词");
        @JsonValue private final int code;
        private final String name;
        FilterType(int code, String name) { this.code = code; this.name = name; }
    }

    @Getter
    public enum FilterOperator implements IEnum {
        EQ(0, "等于"), NE(1, "不等于"), GT(2, "大于"), GTE(3, "大于等于"),
        LT(4, "小于"), LTE(5, "小于等于"), IN(6, "包含于"), NOT_IN(7, "不包含于"),
        IS_NULL(8, "为空"), IS_NOT_NULL(9, "非空"), LIKE(10, "模糊匹配");
        @JsonValue private final int code;
        private final String name;
        FilterOperator(int code, String name) { this.code = code; this.name = name; }
    }

    @Getter
    public enum SortDirection implements IEnum {
        ASC(0, "升序"), DESC(1, "降序");
        @JsonValue private final int code;
        private final String name;
        SortDirection(int code, String name) { this.code = code; this.name = name; }
    }

    @Getter
    public enum AggregateFunction implements IEnum {
        COUNT(0, "计数"), SUM(1, "求和"), MIN(2, "最小值"), MAX(3, "最大值"), AVG(4, "平均值");
        @JsonValue private final int code;
        private final String name;
        AggregateFunction(int code, String name) { this.code = code; this.name = name; }
    }

    @Getter
    public enum CommandType implements IEnum {
        INSERT(0, "新增"), UPDATE(1, "更新"), DELETE(2, "删除");
        @JsonValue private final int code;
        private final String name;
        CommandType(int code, String name) { this.code = code; this.name = name; }
    }

    @Getter
    public enum TransactionMode implements IEnum {
        LOCAL(0, "本地事务"), BEST_EFFORT(1, "尽力而为"), ATOMIC(2, "原子提交");
        @JsonValue private final int code;
        private final String name;
        TransactionMode(int code, String name) { this.code = code; this.name = name; }
    }
}
