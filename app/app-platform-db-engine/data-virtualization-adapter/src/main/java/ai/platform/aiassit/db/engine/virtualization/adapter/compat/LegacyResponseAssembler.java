package ai.platform.aiassit.db.engine.virtualization.adapter.compat;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryListResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将标准虚拟查询结果恢复为旧 DbQuery 响应形状。 */
public class LegacyResponseAssembler {

    public DbQueryGetResponse assembleGet(VirtualQueryResponse source, Collection<String> outputFields) {
        DbQueryGetResponse response = new DbQueryGetResponse();
        if (source != null && source.getRecords() != null && !source.getRecords().isEmpty()) {
            response.setRecord(detailRecord(source.getRecords().get(0), outputFields));
        }
        return response;
    }

    public DbQueryListResponse assembleList(
            VirtualQueryResponse source,
            int page,
            int pageSize,
            Collection<String> outputFields
    ) {
        DbQueryListResponse response = new DbQueryListResponse();
        List<Map<String, Object>> records = source == null ? null : source.getRecords();
        if (records != null) {
            response.setList(records.stream().map(row -> detailRecord(row, outputFields)).toList());
        }
        DbQueryListResponse.PageInfo pageInfo = new DbQueryListResponse.PageInfo();
        pageInfo.setPage(page);
        pageInfo.setSize(pageSize);
        pageInfo.setTotal(total(source));
        response.setPageInfo(pageInfo);
        // LIST 的旧协议始终返回空 summary，不透传虚拟层的摘要。
        response.setSummary(new LinkedHashMap<>());
        return response;
    }

    public DbQueryCountResponse assembleCount(
            VirtualQueryResponse source,
            int page,
            int pageSize,
            boolean plainCount
    ) {
        DbQueryCountResponse response = new DbQueryCountResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(total(source));
        if (plainCount) {
            Map<String, Object> count = new LinkedHashMap<>();
            count.put("count", total(source));
            response.setRecords(new ArrayList<>(List.of(count)));
            response.setSummary(new LinkedHashMap<>(count));
            return response;
        }
        response.setRecords(copyRows(source == null ? null : source.getRecords()));
        response.setSummary(copyMap(source == null ? null : source.getSummary()));
        return response;
    }

    public DbQueryAggregateResponse assembleAggregate(VirtualQueryResponse source, int page, int pageSize) {
        DbQueryAggregateResponse response = new DbQueryAggregateResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(total(source));
        response.setRecords(copyRows(source == null ? null : source.getRecords()));
        response.setSummary(copyMap(source == null ? null : source.getSummary()));
        return response;
    }

    Map<String, Object> detailRecord(Map<String, Object> source, Collection<String> outputFields) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        if (outputFields == null || outputFields.isEmpty()) {
            source.forEach((field, value) -> {
                if (field != null && !field.contains(".")) {
                    result.put(field, value);
                }
            });
        } else {
            for (String field : outputFields) {
                if (field != null && !field.isBlank()) {
                    String relationCode = collectionRelationCode(source, field);
                    if (relationCode != null) {
                        result.putIfAbsent(relationCode, collectionValue(source.get(relationCode), relationCode, outputFields));
                        continue;
                    }
                    putDetailValue(result, field, source.get(field));
                }
            }
        }
        collapseNullMaps(result);
        return result;
    }

    private String collectionRelationCode(Map<String, Object> source, String fieldPath) {
        int separator = fieldPath.indexOf('.');
        if (separator <= 0) return null;
        String relationCode = fieldPath.substring(0, separator);
        return source.get(relationCode) instanceof Collection<?> ? relationCode : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> collectionValue(
            Object source,
            String relationCode,
            Collection<String> outputFields
    ) {
        if (!(source instanceof Collection<?> collection)) return List.of();
        String prefix = relationCode + ".";
        List<String> childFields = outputFields.stream()
                .filter(field -> field != null && field.startsWith(prefix))
                .map(field -> field.substring(prefix.length()))
                .toList();
        List<Map<String, Object>> result = new ArrayList<>(collection.size());
        for (Object rawItem : collection) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) continue;
            Map<String, Object> sourceItem = (Map<String, Object>) rawMap;
            Map<String, Object> item = new LinkedHashMap<>();
            for (String childField : childFields) {
                putDetailValue(item, childField, sourceItem.get(childField));
            }
            result.add(item);
        }
        return result;
    }

    private void putDetailValue(Map<String, Object> target, String fieldPath, Object value) {
        String[] parts = fieldPath.split("\\.");
        Map<String, Object> current = target;
        for (int index = 0; index < parts.length - 1; index++) {
            current = nestedMap(current, parts[index]);
        }
        current.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> target, String key) {
        Object existing = target.get(key);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> nested = new LinkedHashMap<>();
        target.put(key, nested);
        return nested;
    }

    @SuppressWarnings("unchecked")
    private void collapseNullMaps(Map<String, Object> target) {
        List<String> nullKeys = new ArrayList<>();
        for (Map.Entry<String, Object> entry : target.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> rawNested) {
                Map<String, Object> nested = (Map<String, Object>) rawNested;
                collapseNullMaps(nested);
                if (allNull(nested)) {
                    nullKeys.add(entry.getKey());
                }
            }
        }
        nullKeys.forEach(key -> target.put(key, null));
    }

    private boolean allNull(Map<String, Object> target) {
        if (target.isEmpty()) {
            return true;
        }
        for (Object value : target.values()) {
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) nested;
                if (!allNull(typed)) {
                    return false;
                }
            } else if (value != null) {
                return false;
            }
        }
        return true;
    }

    private long total(VirtualQueryResponse source) {
        return source == null || source.getTotal() == null ? 0L : source.getTotal();
    }

    private List<Map<String, Object>> copyRows(List<Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>(source.size());
        for (Map<String, Object> row : source) {
            result.add(copyMap(row));
        }
        return result;
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }
}
