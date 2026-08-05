package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 受控数据预览结果，不包含任何物理执行细节。 */
@Data
public class DataPreviewQueryResponse {

    private String model;
    private String sourceRevision;
    private Long catalogVersion;
    private String queryType;
    private List<Column> columns = new ArrayList<>();
    private List<Map<String, Object>> records = new ArrayList<>();
    private Long total = 0L;
    private Boolean truncated = false;
    private String requestId;
    private Long executionMs;
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Data
    public static class Column {
        private String key;
        private String field;
        private String label;
        /** 标准逻辑类型，供下游 Render JSON 生成使用。 */
        private String dataType;
        private String aggregation;
    }
}
