package ai.platform.aiassit.db.engine.executor.mongodb.support;

import ai.platform.aiassit.db.engine.executor.spi.model.DbQueryColumn;
import org.bson.BsonRegularExpression;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** BSON 值到 API 安全值、查询列类型的统一转换。 */
public final class MongoBsonSupport {

    private MongoBsonSupport() {
    }

    public static Map<String, Object> toSafeDocument(Document document) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (document == null) {
            return safe;
        }
        document.forEach((key, value) -> safe.put(key, toSafeValue(value)));
        return safe;
    }

    public static Object toSafeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Decimal128 decimal) {
            if (decimal.isFinite()) {
                return decimal.bigDecimalValue();
            }
            return decimal.toString();
        }
        if (value instanceof Number) {
            return value;
        }
        if (value instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        if (value instanceof Binary binary) {
            return Base64.getEncoder().encodeToString(binary.getData());
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof Pattern pattern) {
            return pattern.pattern();
        }
        if (value instanceof BsonRegularExpression regex) {
            return regex.getPattern();
        }
        if (value instanceof BsonTimestamp timestamp) {
            return Map.of("time", timestamp.getTime(), "inc", timestamp.getInc());
        }
        if (value instanceof Document document) {
            return toSafeDocument(document);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            map.forEach((key, item) -> safe.put(String.valueOf(key), toSafeValue(item)));
            return safe;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> safe = new ArrayList<>();
            iterable.forEach(item -> safe.add(toSafeValue(item)));
            return safe;
        }
        return String.valueOf(value);
    }

    public static List<DbQueryColumn> queryColumns(List<Document> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        rows.forEach(row -> names.addAll(row.keySet()));
        List<DbQueryColumn> columns = new ArrayList<>();
        for (String name : names) {
            Set<String> types = new LinkedHashSet<>();
            Integer jdbcType = null;
            for (Document row : rows) {
                if (!row.containsKey(name) || row.get(name) == null) {
                    continue;
                }
                Object value = row.get(name);
                types.add(typeName(value));
                int candidate = jdbcType(value);
                jdbcType = jdbcType == null || jdbcType == candidate ? candidate : Types.OTHER;
            }
            String typeName = types.isEmpty() ? "NULL" : types.size() == 1 ? types.iterator().next() : "MIXED";
            columns.add(DbQueryColumn.builder()
                    .name(name)
                    .label(name)
                    .jdbcType(jdbcType == null ? Types.NULL : jdbcType)
                    .typeName(typeName)
                    .build());
        }
        return columns;
    }

    public static String typeName(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String || value instanceof Character) {
            return "STRING";
        }
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return "INT32";
        }
        if (value instanceof Long || value instanceof BsonTimestamp) {
            return "INT64";
        }
        if (value instanceof Float || value instanceof Double) {
            return "DOUBLE";
        }
        if (value instanceof Decimal128 || value instanceof BigDecimal) {
            return "DECIMAL128";
        }
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        if (value instanceof Date || value instanceof Instant) {
            return "DATE_TIME";
        }
        if (value instanceof ObjectId) {
            return "OBJECT_ID";
        }
        if (value instanceof Binary || value instanceof byte[]) {
            return "BINARY";
        }
        if (value instanceof Iterable<?> || value.getClass().isArray()) {
            return "ARRAY";
        }
        if (value instanceof Map<?, ?>) {
            return "OBJECT";
        }
        if (value instanceof Pattern || value instanceof BsonRegularExpression) {
            return "REGEX";
        }
        return value.getClass().getSimpleName().toUpperCase();
    }

    public static int jdbcType(Object value) {
        if (value == null) {
            return Types.NULL;
        }
        if (value instanceof String || value instanceof Character || value instanceof ObjectId
                || value instanceof UUID || value instanceof Pattern || value instanceof BsonRegularExpression) {
            return Types.VARCHAR;
        }
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return Types.INTEGER;
        }
        if (value instanceof Long || value instanceof BsonTimestamp) {
            return Types.BIGINT;
        }
        if (value instanceof Float || value instanceof Double) {
            return Types.DOUBLE;
        }
        if (value instanceof Decimal128 || value instanceof BigDecimal) {
            return Types.DECIMAL;
        }
        if (value instanceof Boolean) {
            return Types.BOOLEAN;
        }
        if (value instanceof Date || value instanceof Instant) {
            return Types.TIMESTAMP;
        }
        if (value instanceof Binary || value instanceof byte[]) {
            return Types.BINARY;
        }
        if (value instanceof Iterable<?> || value.getClass().isArray()) {
            return Types.ARRAY;
        }
        return Types.JAVA_OBJECT;
    }
}
