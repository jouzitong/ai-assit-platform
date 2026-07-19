package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent DataContract 的受控查询形态。
 *
 * <p>所有字段都必须是虚拟字段编码；关系字段使用已发布的 {@code relationCode.fieldCode}。</p>
 */
@Data
public class DataPreviewQueryRequest {

    private String model;

    private String sourceRevision;

    private Long catalogVersion;

    private List<Measure> measures = new ArrayList<>();

    private List<Dimension> dimensions = new ArrayList<>();

    private TimeRange timeRange;

    private List<Filter> filters = new ArrayList<>();

    private List<Sort> sorts = new ArrayList<>();

    private Integer limit = 20;

    @Data
    public static class Measure {
        private String field;
        private String aggregation;
        private String label;
        private String alias;
    }

    @Data
    public static class Dimension {
        private String field;
        private String label;
        private String alias;
    }

    @Data
    public static class TimeRange {
        private String field;
        private String preset;
        private Object start;
        private Object end;
    }

    @Data
    public static class Filter {
        private String field;
        private String operator;
        private Object value;
        private List<Object> values = new ArrayList<>();
    }

    @Data
    public static class Sort {
        private String field;
        private String direction;
    }
}
