package ai.platform.aiassit.db.engine.virtualization.adapter.physical;

import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommandSpec;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilter;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilterOperator;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilterType;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalProjection;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalQuerySpec;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Renders only the database-independent, validated semantics exposed by virtualization SPI. */
final class ControlledSqlRenderer {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    private ControlledSqlRenderer() {
    }

    static RenderedSql query(PhysicalQuerySpec spec, int limit, DbAccessDbType dbType) {
        requireRelational(dbType);
        if (spec == null) {
            throw invalid("physical query spec 不能为空");
        }
        String table = quotePath(spec.table(), dbType);
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        if (dbType == DbAccessDbType.SQL_SERVER && !spec.countOnly()) {
            sql.append("TOP (").append(requirePositive(limit, "query limit")).append(") ");
        }
        if (spec.countOnly()) {
            sql.append("COUNT(1) AS ").append(quote("__count", dbType));
        } else {
            sql.append(projections(spec.projections(), dbType));
        }
        sql.append(" FROM ").append(table);
        if (spec.filter() != null) {
            sql.append(" WHERE ").append(filter(spec.filter(), dbType, parameters));
        }
        if (!spec.countOnly() && dbType != DbAccessDbType.SQL_SERVER) {
            int safeLimit = requirePositive(limit, "query limit");
            if (dbType == DbAccessDbType.ORACLE || dbType == DbAccessDbType.DM8) {
                sql.append(" FETCH FIRST ").append(safeLimit).append(" ROWS ONLY");
            } else {
                sql.append(" LIMIT ").append(safeLimit);
            }
        }
        return new RenderedSql(sql.toString(), parameters);
    }

    static RenderedSql command(PhysicalCommandSpec spec, DbAccessDbType dbType) {
        requireRelational(dbType);
        if (spec == null || spec.type() == null) {
            throw invalid("physical command spec/type 不能为空");
        }
        return switch (spec.type()) {
            case INSERT -> insert(spec, dbType);
            case UPDATE -> update(spec, dbType);
            case DELETE -> delete(spec, dbType);
        };
    }

    private static String projections(List<PhysicalProjection> projections, DbAccessDbType dbType) {
        if (projections == null || projections.isEmpty()) {
            throw invalid("物理查询没有可投影字段");
        }
        List<String> rendered = new ArrayList<>(projections.size());
        Set<String> aliases = new LinkedHashSet<>();
        for (PhysicalProjection projection : projections) {
            if (projection == null) {
                throw invalid("物理投影不能为空");
            }
            String alias = requiredIdentifier(projection.alias(), "projection alias");
            if (!aliases.add(alias)) {
                throw invalid("物理投影别名重复: " + alias);
            }
            rendered.add(quotePath(projection.field(), dbType) + " AS " + quote(alias, dbType));
        }
        return String.join(", ", rendered);
    }

    private static RenderedSql insert(PhysicalCommandSpec spec, DbAccessDbType dbType) {
        List<Map<String, Object>> rows = spec.rows();
        if (rows == null || rows.isEmpty() || rows.get(0) == null || rows.get(0).isEmpty()) {
            throw invalid("INSERT 记录和字段不能为空");
        }
        List<String> columns = new ArrayList<>(rows.get(0).keySet());
        columns.forEach(column -> requiredIdentifier(column, "insert column"));
        Set<String> expectedColumns = new LinkedHashSet<>(columns);
        List<Object> parameters = new ArrayList<>(rows.size() * columns.size());
        for (Map<String, Object> row : rows) {
            if (row == null || !new LinkedHashSet<>(row.keySet()).equals(expectedColumns)) {
                throw invalid("同一批 INSERT 的字段集合必须一致");
            }
            columns.forEach(column -> parameters.add(row.get(column)));
        }
        String placeholders = "(" + String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";
        String sql = "INSERT INTO " + quotePath(spec.table(), dbType)
                + " (" + columns.stream().map(column -> quote(column, dbType)).reduce((left, right) -> left + ", " + right).orElseThrow()
                + ") VALUES " + String.join(", ", Collections.nCopies(rows.size(), placeholders));
        return new RenderedSql(sql, parameters);
    }

    private static RenderedSql update(PhysicalCommandSpec spec, DbAccessDbType dbType) {
        if (spec.assignments() == null || spec.assignments().isEmpty()) {
            throw invalid("UPDATE 没有可写物理字段");
        }
        if (spec.filter() == null) {
            throw invalid("UPDATE 必须提供过滤条件");
        }
        List<Object> parameters = new ArrayList<>();
        List<String> assignments = new ArrayList<>();
        spec.assignments().forEach((column, value) -> {
            assignments.add(quote(requiredIdentifier(column, "update column"), dbType) + " = ?");
            parameters.add(value);
        });
        String where = filter(spec.filter(), dbType, parameters);
        return new RenderedSql("UPDATE " + quotePath(spec.table(), dbType)
                + " SET " + String.join(", ", assignments) + " WHERE " + where, parameters);
    }

    private static RenderedSql delete(PhysicalCommandSpec spec, DbAccessDbType dbType) {
        if (spec.filter() == null) {
            throw invalid("DELETE 必须提供过滤条件");
        }
        List<Object> parameters = new ArrayList<>();
        String where = filter(spec.filter(), dbType, parameters);
        return new RenderedSql("DELETE FROM " + quotePath(spec.table(), dbType) + " WHERE " + where, parameters);
    }

    private static String filter(PhysicalFilter node, DbAccessDbType dbType, List<Object> parameters) {
        if (node == null || node.type() == null) {
            throw invalid("物理过滤节点/type 不能为空");
        }
        if (node.type() == PhysicalFilterType.PREDICATE) {
            return predicate(node, dbType, parameters);
        }
        List<PhysicalFilter> children = node.children();
        if (children == null || children.isEmpty()) {
            throw invalid("逻辑过滤节点必须包含 child");
        }
        if (node.type() == PhysicalFilterType.NOT && children.size() != 1) {
            throw invalid("NOT 过滤节点必须且只能有一个 child");
        }
        String joiner = node.type() == PhysicalFilterType.OR ? " OR " : " AND ";
        String body = children.stream()
                .map(child -> "(" + filter(child, dbType, parameters) + ")")
                .reduce((left, right) -> left + joiner + right)
                .orElseThrow();
        return node.type() == PhysicalFilterType.NOT ? "NOT (" + body + ")" : body;
    }

    private static String predicate(PhysicalFilter node, DbAccessDbType dbType, List<Object> parameters) {
        if (node.operator() == null) {
            throw invalid("物理过滤谓词/operator 不能为空");
        }
        String column = quotePath(node.field(), dbType);
        PhysicalFilterOperator operator = node.operator();
        return switch (operator) {
            case IS_NULL -> column + " IS NULL";
            case IS_NOT_NULL -> column + " IS NOT NULL";
            case IN, NOT_IN -> inPredicate(column, operator, node, parameters);
            case LIKE -> likePredicate(column, node.value(), "%", "%", parameters);
            case STARTS_WITH -> likePredicate(column, node.value(), "", "%", parameters);
            case ENDS_WITH -> likePredicate(column, node.value(), "%", "", parameters);
            case EQ -> comparison(column, "=", node.value(), true, parameters);
            case NE -> comparison(column, "<>", node.value(), false, parameters);
            case GT -> comparison(column, ">", requiredValue(node.value(), operator), true, parameters);
            case GTE -> comparison(column, ">=", requiredValue(node.value(), operator), true, parameters);
            case LT -> comparison(column, "<", requiredValue(node.value(), operator), true, parameters);
            case LTE -> comparison(column, "<=", requiredValue(node.value(), operator), true, parameters);
        };
    }

    private static String comparison(
            String column,
            String operator,
            Object value,
            boolean nullMeansIsNull,
            List<Object> parameters
    ) {
        if (value == null) {
            return column + (nullMeansIsNull ? " IS NULL" : " IS NOT NULL");
        }
        parameters.add(value);
        return column + " " + operator + " ?";
    }

    private static String likePredicate(
            String column,
            Object value,
            String prefix,
            String suffix,
            List<Object> parameters
    ) {
        parameters.add(prefix + String.valueOf(requiredValue(value, PhysicalFilterOperator.LIKE)) + suffix);
        return column + " LIKE ?";
    }

    private static String inPredicate(
            String column,
            PhysicalFilterOperator operator,
            PhysicalFilter node,
            List<Object> parameters
    ) {
        Collection<?> values = node.values();
        if ((values == null || values.isEmpty()) && node.value() instanceof Collection<?> collection) {
            values = collection;
        }
        if (values == null || values.isEmpty()) {
            throw invalid(operator + " 条件不能为空");
        }
        parameters.addAll(values);
        return column + (operator == PhysicalFilterOperator.NOT_IN ? " NOT IN (" : " IN (")
                + String.join(", ", Collections.nCopies(values.size(), "?")) + ")";
    }

    private static Object requiredValue(Object value, PhysicalFilterOperator operator) {
        if (value == null) {
            throw invalid(operator + " 条件值不能为空");
        }
        return value;
    }

    private static String quotePath(String path, DbAccessDbType dbType) {
        if (path == null || path.isBlank()) {
            throw invalid("物理标识符不能为空");
        }
        return java.util.Arrays.stream(path.trim().split("\\.", -1))
                .map(part -> quote(part, dbType))
                .reduce((left, right) -> left + "." + right)
                .orElseThrow();
    }

    private static String quote(String identifier, DbAccessDbType dbType) {
        String safe = requiredIdentifier(identifier, "physical identifier");
        return switch (dbType) {
            case MYSQL, OCEANBASE, TDSQL, GOLDENDB -> "`" + safe + "`";
            case SQL_SERVER -> "[" + safe + "]";
            default -> "\"" + safe + "\"";
        };
    }

    private static String requiredIdentifier(String identifier, String label) {
        if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) {
            throw invalid(label + " 非法: " + identifier);
        }
        return identifier;
    }

    private static int requirePositive(int value, String label) {
        if (value <= 0) {
            throw invalid(label + " 必须大于 0");
        }
        return value;
    }

    private static void requireRelational(DbAccessDbType dbType) {
        if (dbType == null) {
            throw invalid("数据库类型不能为空");
        }
        if (dbType == DbAccessDbType.MONGODB) {
            throw invalid("当前物理关系型计划不支持 MongoDB");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    record RenderedSql(String sql, List<Object> parameters) {
        RenderedSql {
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
        }
    }
}
