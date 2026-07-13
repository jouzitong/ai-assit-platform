package ai.platform.aiassit.data.virtualization.core.plan;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class PhysicalSqlRenderer {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    public Rendered render(
            CatalogSnapshot snapshot,
            CatalogSnapshot.Binding binding,
            DbAccessDbType dbType,
            VirtualLogicalPlan logicalPlan,
            List<CatalogSnapshot.TransformRule> rules,
            Map<Long, String> aliases
    ) {
        if (dbType == DbAccessDbType.MONGODB) {
            throw new VirtualDataException("FIELD_TRANSFORM_PUSHDOWN_UNSUPPORTED", "虚拟关系型计划暂不支持 MongoDB 绑定");
        }
        Map<String, String> pushdownFields = pushdownFields(snapshot, rules);
        boolean filterPushed = isPushable(logicalPlan.filter(), pushdownFields);
        boolean countOnly = logicalPlan.queryType() == QueryType.COUNT && filterPushed;
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        if (dbType == DbAccessDbType.SQL_SERVER && !countOnly) {
            sql.append("TOP (").append(scanLimit(logicalPlan.maxScanRows())).append(") ");
        }
        if (countOnly) {
            sql.append("COUNT(1) AS ").append(quote("__count", dbType));
        } else {
            if (aliases.isEmpty()) {
                throw new VirtualDataException("FIELD_NOT_MAPPED", "物理查询没有可投影字段");
            }
            List<String> columns = new ArrayList<>();
            Map<Long, CatalogSnapshot.Port> physicalPorts = new LinkedHashMap<>();
            rules.forEach(rule -> rule.physicalPorts().forEach(port -> physicalPorts.putIfAbsent(port.physicalFieldMetaId(), port)));
            physicalPorts.forEach((fieldId, port) -> columns.add(
                    quotePath(port.physicalColumnName(), dbType) + " AS " + quote(aliases.get(fieldId), dbType)));
            sql.append(String.join(", ", columns));
        }
        sql.append(" FROM ").append(quotePath(binding.physicalTableName(), dbType));
        if (filterPushed && logicalPlan.filter() != null) {
            sql.append(" WHERE ").append(renderFilter(logicalPlan.filter(), pushdownFields, dbType, parameters));
        }
        if (!countOnly) {
            sql.append(limit(scanLimit(logicalPlan.maxScanRows()), dbType));
        }
        return new Rendered(sql.toString(), parameters, filterPushed, countOnly);
    }

    public Map<String, String> pushdownFields(CatalogSnapshot snapshot, List<CatalogSnapshot.TransformRule> rules) {
        Map<String, String> result = new LinkedHashMap<>();
        for (CatalogSnapshot.TransformRule rule : rules) {
            if (!"identity".equals(rule.readTransformerCode()) || rule.physicalPorts().size() != 1 || rule.virtualPorts().size() != 1) continue;
            CatalogSnapshot.VirtualField field = snapshot.fieldsById().get(rule.virtualPorts().get(0).virtualFieldId());
            if (field != null) result.put(field.code(), rule.physicalPorts().get(0).physicalColumnName());
        }
        return result;
    }

    private boolean isPushable(FilterNode node, Map<String, String> fields) {
        if (node == null) return true;
        if (node.getType() == FilterType.PREDICATE) return fields.containsKey(node.getField());
        return node.getChildren() != null && node.getChildren().stream().allMatch(child -> isPushable(child, fields));
    }

    private String renderFilter(FilterNode node, Map<String, String> fields, DbAccessDbType dbType, List<Object> parameters) {
        if (node.getType() == FilterType.PREDICATE) {
            String column = quotePath(fields.get(node.getField()), dbType);
            FilterOperator operator = node.getOperator();
            return switch (operator) {
                case IS_NULL -> column + " IS NULL";
                case IS_NOT_NULL -> column + " IS NOT NULL";
                case IN, NOT_IN -> renderIn(column, operator, node, parameters);
                case LIKE -> {
                    parameters.add("%" + node.getValue() + "%");
                    yield column + " LIKE ?";
                }
                default -> {
                    parameters.add(node.getValue());
                    yield column + " " + sqlOperator(operator) + " ?";
                }
            };
        }
        String joiner = node.getType() == FilterType.OR ? " OR " : " AND ";
        String expression = node.getChildren().stream()
                .map(child -> "(" + renderFilter(child, fields, dbType, parameters) + ")")
                .reduce((left, right) -> left + joiner + right).orElse("1=1");
        return node.getType() == FilterType.NOT ? "NOT (" + expression + ")" : expression;
    }

    private String renderIn(String column, FilterOperator operator, FilterNode node, List<Object> parameters) {
        Collection<?> values = node.getValues() == null || node.getValues().isEmpty()
                ? (node.getValue() instanceof Collection<?> collection ? collection : List.of()) : node.getValues();
        if (values.isEmpty()) throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "IN 条件不能为空");
        parameters.addAll(values);
        return column + (operator == FilterOperator.NOT_IN ? " NOT IN (" : " IN (")
                + String.join(",", java.util.Collections.nCopies(values.size(), "?")) + ")";
    }

    private String sqlOperator(FilterOperator operator) {
        return switch (operator) {
            case EQ -> "="; case NE -> "<>"; case GT -> ">"; case GTE -> ">="; case LT -> "<"; case LTE -> "<=";
            default -> throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "不支持的 SQL 操作符: " + operator);
        };
    }

    private String limit(int maxRows, DbAccessDbType dbType) {
        if (dbType == DbAccessDbType.SQL_SERVER) return "";
        return dbType == DbAccessDbType.ORACLE || dbType == DbAccessDbType.DM8
                ? " FETCH FIRST " + maxRows + " ROWS ONLY" : " LIMIT " + maxRows;
    }

    private int scanLimit(int maxRows) {
        return maxRows == Integer.MAX_VALUE ? maxRows : maxRows + 1;
    }

    private String quotePath(String identifier, DbAccessDbType dbType) {
        if (identifier == null || identifier.isBlank()) throw new VirtualDataException("FIELD_NOT_MAPPED", "物理标识符为空");
        return java.util.Arrays.stream(identifier.split("\\.")).map(part -> quote(part, dbType)).reduce((a, b) -> a + "." + b).orElseThrow();
    }

    private String quote(String identifier, DbAccessDbType dbType) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new VirtualDataException("FIELD_NOT_MAPPED", "非法物理标识符: " + identifier);
        }
        return switch (dbType) {
            case MYSQL, OCEANBASE, TDSQL, GOLDENDB -> "`" + identifier + "`";
            case SQL_SERVER -> "[" + identifier + "]";
            default -> "\"" + identifier + "\"";
        };
    }

    public record Rendered(String sql, List<Object> parameters, boolean filterPushed, boolean countOnly) {
    }
}
