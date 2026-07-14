package ai.platform.aiassit.db.engine.virtualization.adapter.compat;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountDimension;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountMetric;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotExt;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/** 将虚拟 AGGREGATE 的扁平结果组装为旧透视响应。 */
public class PivotAssembler {

    static final String INVALID_PIVOT = "LEGACY_PIVOT_INVALID";
    static final String UNSUPPORTED_OPTION = "LEGACY_PIVOT_OPTION_UNSUPPORTED";
    static final String COLUMN_KEY_COLLISION = "LEGACY_PIVOT_COLUMN_KEY_COLLISION";

    public DbQueryPivotResponse assemble(VirtualQueryResponse source, DbQueryPivotRequest request) {
        validateRequest(request);
        DbQueryPivotExt ext = request.getExt() == null ? new DbQueryPivotExt() : request.getExt();
        if (hasText(ext.getTimeGrain())) {
            throw error(UNSUPPORTED_OPTION, "query.pivot 暂不支持 timeGrain");
        }
        if (ext.getTopN() != null) {
            throw error(UNSUPPORTED_OPTION, "query.pivot 的 topN 语义尚未版本化，暂不支持");
        }

        List<String> rowFields = request.getRows().stream().map(this::dimensionAlias).toList();
        List<String> columnFields = request.getColumns().stream().map(this::dimensionAlias).toList();
        List<String> metricFields = request.getMetrics().stream().map(this::metricAlias).toList();
        List<Map<String, Object>> records = source == null || source.getRecords() == null
                ? List.of() : source.getRecords();

        Map<List<Object>, Map<String, Object>> pivotRows = new LinkedHashMap<>();
        Set<String> pivotColumns = new LinkedHashSet<>();
        Map<String, List<Object>> displayKeys = new LinkedHashMap<>();
        for (Map<String, Object> record : records) {
            List<Object> rowKey = compositeKey(record, rowFields);
            Map<String, Object> pivotRow = pivotRows.computeIfAbsent(rowKey, ignored -> {
                Map<String, Object> created = new LinkedHashMap<>();
                rowFields.forEach(field -> created.put(field, record.get(field)));
                return created;
            });

            List<Object> structuralColumnKey = compositeKey(record, columnFields);
            String displayColumnKey = displayKey(structuralColumnKey);
            List<Object> previous = displayKeys.putIfAbsent(displayColumnKey, structuralColumnKey);
            if (previous != null && !previous.equals(structuralColumnKey)) {
                throw error(COLUMN_KEY_COLLISION, "透视列显示 key 冲突: " + displayColumnKey);
            }
            for (String metricField : metricFields) {
                String pivotColumn = metricFields.size() == 1
                        ? displayColumnKey : displayColumnKey + ":" + metricField;
                pivotColumns.add(pivotColumn);
                pivotRow.put(pivotColumn, record.get(metricField));
            }
        }

        List<Map<String, Object>> resultRecords = new ArrayList<>(pivotRows.values());
        for (Map<String, Object> row : resultRecords) {
            for (String column : pivotColumns) {
                row.putIfAbsent(column, ext.getFillValue());
            }
        }

        DbQueryPivotResponse response = new DbQueryPivotResponse();
        response.setColumnKeys(new ArrayList<>(pivotColumns));
        response.setRecords(resultRecords);
        if (records.size() == 1) {
            response.setSummary(new LinkedHashMap<>(records.get(0)));
        } else {
            response.setSummary(new LinkedHashMap<>());
        }
        return response;
    }

    private void validateRequest(DbQueryPivotRequest request) {
        if (request == null || request.getRows() == null || request.getRows().isEmpty()
                || request.getColumns() == null || request.getColumns().isEmpty()
                || request.getMetrics() == null || request.getMetrics().isEmpty()) {
            throw error(INVALID_PIVOT, "query.pivot 必须提供 rows、columns 和 metrics");
        }
    }

    private List<Object> compositeKey(Map<String, Object> record, List<String> fields) {
        List<Object> key = new ArrayList<>(fields.size());
        for (String field : fields) {
            key.add(record == null ? null : record.get(field));
        }
        return Collections.unmodifiableList(key);
    }

    private String displayKey(List<Object> values) {
        StringJoiner joiner = new StringJoiner("|");
        values.forEach(value -> joiner.add(Objects.toString(value, "")));
        return joiner.toString();
    }

    private String dimensionAlias(DbQueryCountDimension dimension) {
        if (dimension == null || !hasText(dimension.getField())) {
            throw error(INVALID_PIVOT, "透视维度 field 不能为空");
        }
        return hasText(dimension.getAlias()) ? dimension.getAlias().trim() : dimension.getField().trim();
    }

    private String metricAlias(DbQueryCountMetric metric) {
        if (metric == null) {
            throw error(INVALID_PIVOT, "透视指标不能为空");
        }
        if (hasText(metric.getAlias())) {
            return metric.getAlias().trim();
        }
        String function = hasText(metric.getFunc()) ? metric.getFunc().trim().toLowerCase(Locale.ROOT) : "count";
        String field = hasText(metric.getField()) ? metric.getField().trim() : "all";
        return function + "_" + field;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private LegacyQueryCompatibilityException error(String code, String message) {
        return new LegacyQueryCompatibilityException(code, message);
    }
}
