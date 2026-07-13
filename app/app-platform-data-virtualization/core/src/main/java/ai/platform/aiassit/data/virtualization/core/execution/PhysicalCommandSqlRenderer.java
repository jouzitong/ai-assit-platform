package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
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
public class PhysicalCommandSqlRenderer {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    public CommandSql insert(String table, List<Map<String, Object>> rows, DbAccessDbType dbType) {
        requireRelational(dbType);
        if (rows == null || rows.isEmpty()) throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "INSERT 记录不能为空");
        List<String> columns = new ArrayList<>(rows.get(0).keySet());
        if (columns.isEmpty()) throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "INSERT 没有可写物理字段");
        for (Map<String, Object> row : rows) {
            if (!row.keySet().equals(rows.get(0).keySet())) {
                throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "同一批 INSERT 的字段集合必须一致");
            }
        }
        String placeholders = "(" + String.join(",", java.util.Collections.nCopies(columns.size(), "?")) + ")";
        String sql = "INSERT INTO " + quotePath(table, dbType) + " ("
                + columns.stream().map(column -> quote(column, dbType)).reduce((a, b) -> a + "," + b).orElseThrow()
                + ") VALUES " + String.join(",", java.util.Collections.nCopies(rows.size(), placeholders));
        List<Object> parameters = new ArrayList<>();
        rows.forEach(row -> columns.forEach(column -> parameters.add(row.get(column))));
        return new CommandSql(sql, parameters);
    }

    public CommandSql update(
            String table,
            Map<String, Object> values,
            FilterNode filter,
            Map<String, String> filterFields,
            DbAccessDbType dbType
    ) {
        requireRelational(dbType);
        if (values == null || values.isEmpty()) throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "UPDATE 没有可写物理字段");
        if (filter == null) throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "UPDATE 必须提供过滤条件");
        validateFilter(filter);
        List<Object> parameters = new ArrayList<>();
        List<String> assignments = new ArrayList<>();
        values.forEach((column, value) -> { assignments.add(quote(column, dbType) + " = ?"); parameters.add(value); });
        String where = renderFilter(filter, filterFields, dbType, parameters);
        return new CommandSql("UPDATE " + quotePath(table, dbType) + " SET " + String.join(",", assignments) + " WHERE " + where, parameters);
    }

    public CommandSql delete(String table, FilterNode filter, Map<String, String> filterFields, DbAccessDbType dbType) {
        requireRelational(dbType);
        if (filter == null) throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "DELETE 必须提供过滤条件");
        validateFilter(filter);
        List<Object> parameters = new ArrayList<>();
        return new CommandSql("DELETE FROM " + quotePath(table, dbType) + " WHERE "
                + renderFilter(filter, filterFields, dbType, parameters), parameters);
    }

    private String renderFilter(FilterNode node, Map<String, String> fields, DbAccessDbType dbType, List<Object> parameters) {
        if (node.getType() == FilterType.PREDICATE) {
            String physical = fields.get(node.getField());
            if (physical == null) throw new VirtualDataException("FIELD_TRANSFORM_PUSHDOWN_UNSUPPORTED", "写过滤字段不能安全下推: " + node.getField());
            String column = quote(physical, dbType);
            return switch (node.getOperator()) {
                case IS_NULL -> column + " IS NULL";
                case IS_NOT_NULL -> column + " IS NOT NULL";
                case IN, NOT_IN -> renderIn(column, node, parameters);
                case LIKE -> { parameters.add("%" + node.getValue() + "%"); yield column + " LIKE ?"; }
                default -> { parameters.add(node.getValue()); yield column + " " + operator(node.getOperator()) + " ?"; }
            };
        }
        String joiner = node.getType() == FilterType.OR ? " OR " : " AND ";
        String body = node.getChildren().stream().map(child -> "(" + renderFilter(child, fields, dbType, parameters) + ")")
                .reduce((left, right) -> left + joiner + right).orElseThrow();
        return node.getType() == FilterType.NOT ? "NOT (" + body + ")" : body;
    }

    private String renderIn(String column, FilterNode node, List<Object> parameters) {
        Collection<?> values = node.getValues() == null || node.getValues().isEmpty()
                ? (node.getValue() instanceof Collection<?> source ? source : List.of()) : node.getValues();
        if (values.isEmpty()) throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "IN 条件不能为空");
        parameters.addAll(values);
        return column + (node.getOperator() == FilterOperator.NOT_IN ? " NOT IN (" : " IN (")
                + String.join(",", java.util.Collections.nCopies(values.size(), "?")) + ")";
    }

    private void validateFilter(FilterNode node) {
        if (node.getType() == null) throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "写过滤节点缺少 type");
        if (node.getType() == FilterType.PREDICATE) {
            if (node.getField() == null || node.getField().isBlank() || node.getOperator() == null) {
                throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "写过滤谓词缺少 field/operator");
            }
            return;
        }
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "写逻辑过滤节点缺少 children");
        }
        if (node.getType() == FilterType.NOT && node.getChildren().size() != 1) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "NOT 写过滤节点必须且只能有一个 child");
        }
        node.getChildren().forEach(this::validateFilter);
    }

    private String operator(FilterOperator operator) {
        return switch (operator) {
            case EQ -> "="; case NE -> "<>"; case GT -> ">"; case GTE -> ">="; case LT -> "<"; case LTE -> "<=";
            default -> throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "不支持的写过滤操作符: " + operator);
        };
    }

    private void requireRelational(DbAccessDbType dbType) {
        if (dbType == DbAccessDbType.MONGODB) throw new VirtualDataException("FIELD_TRANSFORM_WRITE_UNSUPPORTED", "虚拟 SQL 写入暂不支持 MongoDB");
    }

    private String quotePath(String path, DbAccessDbType dbType) {
        return java.util.Arrays.stream(path.split("\\.")).map(part -> quote(part, dbType)).reduce((a, b) -> a + "." + b).orElseThrow();
    }

    private String quote(String identifier, DbAccessDbType dbType) {
        if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) throw new VirtualDataException("FIELD_NOT_MAPPED", "非法物理标识符: " + identifier);
        return switch (dbType) {
            case MYSQL, OCEANBASE, TDSQL, GOLDENDB -> "`" + identifier + "`";
            case SQL_SERVER -> "[" + identifier + "]";
            default -> "\"" + identifier + "\"";
        };
    }

    public record CommandSql(String sql, List<Object> parameters) {
    }
}
