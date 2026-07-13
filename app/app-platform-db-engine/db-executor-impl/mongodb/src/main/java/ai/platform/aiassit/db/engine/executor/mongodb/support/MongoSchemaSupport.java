package ai.platform.aiassit.db.engine.executor.mongodb.support;

import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbColumnMeta;
import ai.platform.aiassit.db.engine.executor.spi.model.DbQueryColumn;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableColumnDefinition;
import org.bson.Document;
import org.springframework.util.StringUtils;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** MongoDB JSON Schema 与关系型兼容字段模型之间的映射。 */
public final class MongoSchemaSupport {

    private static final String JSON_SCHEMA = "$jsonSchema";

    private MongoSchemaSupport() {
    }

    public static Document mergeColumns(
            Document existingValidator,
            List<DbTableColumnDefinition> columns,
            String tableComment
    ) throws DbAccessException {
        Document validator = deepCopy(existingValidator);
        Document schema = directSchema(validator, true);
        schema.putIfAbsent("bsonType", "object");
        Document properties = schema.get("properties") instanceof Document value ? value : new Document();
        schema.put("properties", properties);
        LinkedHashSet<String> required = requiredFields(schema);
        Set<String> seen = new LinkedHashSet<>();
        if (columns != null) {
            for (DbTableColumnDefinition column : columns) {
                if (column == null) {
                    throw new DbAccessException("MongoDB 字段定义不能为空");
                }
                validateFieldName(column.getColumnName());
                String name = column.getColumnName().trim();
                if (!seen.add(name)) {
                    throw new DbAccessException("MongoDB 字段定义重复: " + name);
                }
                Document existingProperty = properties.get(name) instanceof Document value ? value : null;
                validateColumn(column, existingProperty == null);
                Document property = existingProperty == null
                        ? buildProperty(column)
                        : mergeProperty(existingProperty, column);
                properties.put(name, property);
                if (Boolean.FALSE.equals(column.getNullable())) {
                    required.add(name);
                } else if (Boolean.TRUE.equals(column.getNullable()) || existingProperty == null) {
                    required.remove(name);
                }
            }
        }
        if (required.isEmpty()) {
            schema.remove("required");
        } else {
            schema.put("required", new ArrayList<>(required));
        }
        if (StringUtils.hasText(tableComment)) {
            schema.put("description", tableComment.trim());
        }
        return validator;
    }

    public static RemoveResult removeColumns(Document existingValidator, Collection<String> columnNames)
            throws DbAccessException {
        Document validator = deepCopy(existingValidator);
        if (!validator.containsKey(JSON_SCHEMA)) {
            if (containsNestedJsonSchema(validator)) {
                throw new DbAccessException("MongoDB 复杂 validator 中的 $jsonSchema 无法安全修改");
            }
            return new RemoveResult(validator, false);
        }
        Document schema = directSchema(validator, false);
        Document properties = schema.get("properties") instanceof Document value ? value : new Document();
        LinkedHashSet<String> required = requiredFields(schema);
        boolean changed = false;
        for (String name : columnNames) {
            changed = properties.remove(name) != null || changed;
            changed = required.remove(name) || changed;
        }
        schema.put("properties", properties);
        if (required.isEmpty()) {
            schema.remove("required");
        } else {
            schema.put("required", new ArrayList<>(required));
        }
        return new RemoveResult(validator, changed);
    }

    public static List<DbColumnMeta> columnsFromValidator(String tableName, Document validator)
            throws DbAccessException {
        if (validator == null || validator.isEmpty()) {
            return new ArrayList<>();
        }
        if (!validator.containsKey(JSON_SCHEMA)) {
            if (containsNestedJsonSchema(validator)) {
                throw new DbAccessException("MongoDB 复杂 validator 中的 $jsonSchema 无法安全读取");
            }
            return new ArrayList<>();
        }
        Document schema = directSchema(deepCopy(validator), false);
        Document properties = schema.get("properties") instanceof Document value ? value : new Document();
        Set<String> required = requiredFields(schema);
        List<DbColumnMeta> columns = new ArrayList<>();
        int ordinal = 1;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!(entry.getValue() instanceof Document definition)) {
                continue;
            }
            String name = entry.getKey();
            columns.add(DbColumnMeta.builder()
                    .tableName(tableName)
                    .columnName(name)
                    .dataType(readType(definition))
                    .columnLength(integerValue(definition.get("maxLength")))
                    .nullable(!required.contains(name) || allowsNull(definition))
                    .primaryKey("_id".equals(name))
                    .ordinalPosition(ordinal++)
                    .columnComment(stringValue(definition.get("description")))
                    .build());
        }
        return columns;
    }

    public static List<DbQueryColumn> queryColumnsFromValidator(Document validator) throws DbAccessException {
        List<DbQueryColumn> result = new ArrayList<>();
        for (DbColumnMeta column : columnsFromValidator("", validator)) {
            result.add(DbQueryColumn.builder()
                    .name(column.getColumnName())
                    .label(column.getColumnName())
                    .jdbcType(jdbcTypeForSchema(column.getDataType()))
                    .typeName(column.getDataType())
                    .build());
        }
        return result;
    }

    public static boolean definesField(Document validator, String fieldName) throws DbAccessException {
        if (validator == null || !validator.containsKey(JSON_SCHEMA)) {
            return false;
        }
        Document schema = directSchema(deepCopy(validator), false);
        return schema.get("properties") instanceof Document properties && properties.containsKey(fieldName);
    }

    public static String tableComment(Document validator) {
        if (validator == null || !(validator.get(JSON_SCHEMA) instanceof Document schema)) {
            return null;
        }
        return stringValue(schema.get("description"));
    }

    public static Document buildProperty(DbTableColumnDefinition column) throws DbAccessException {
        validateColumn(column, true);
        String bsonType = toBsonType(column.getDataType());
        Object declaredType = Boolean.TRUE.equals(column.getNullable())
                ? List.of(bsonType, "null")
                : bsonType;
        Document property = new Document("bsonType", declaredType);
        if (column.getColumnLength() != null) {
            if (!"string".equals(bsonType)) {
                throw new DbAccessException("MongoDB 仅字符串类型支持 columnLength");
            }
            if (column.getColumnLength() <= 0) {
                throw new DbAccessException("MongoDB columnLength 必须大于 0");
            }
            property.put("maxLength", column.getColumnLength());
        }
        if (StringUtils.hasText(column.getColumnComment())) {
            property.put("description", column.getColumnComment().trim());
        }
        return property;
    }

    public static String toBsonType(String dataType) throws DbAccessException {
        if (!StringUtils.hasText(dataType)) {
            throw new DbAccessException("MongoDB 字段类型不能为空");
        }
        String normalized = dataType.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "STRING", "VARCHAR", "CHAR", "TEXT", "CLOB" -> "string";
            case "INT", "INTEGER", "INT32", "SMALLINT", "TINYINT" -> "int";
            case "LONG", "INT64", "BIGINT" -> "long";
            case "DOUBLE", "FLOAT", "REAL" -> "double";
            case "DECIMAL", "DECIMAL128", "NUMERIC", "NUMBER" -> "decimal";
            case "BOOL", "BOOLEAN" -> "bool";
            case "DATE", "DATE_TIME", "DATETIME", "TIMESTAMP" -> "date";
            case "OBJECT_ID", "OBJECTID" -> "objectId";
            case "OBJECT", "DOCUMENT", "JSON" -> "object";
            case "ARRAY", "LIST" -> "array";
            case "BINARY", "BLOB", "BYTEA" -> "binData";
            case "REGEX" -> "regex";
            default -> throw new DbAccessException("不支持的 MongoDB 字段类型: " + dataType);
        };
    }

    public static void validateFieldName(String name) throws DbAccessException {
        if (!StringUtils.hasText(name) || name.indexOf('.') >= 0 || name.startsWith("$") || name.indexOf('\0') >= 0) {
            throw new DbAccessException("MongoDB 字段名仅支持安全的顶层字段: " + name);
        }
    }

    private static Document mergeProperty(Document existing, DbTableColumnDefinition column) throws DbAccessException {
        Document property = deepCopy(existing);
        Object existingType = property.get("bsonType");
        Object baseType = StringUtils.hasText(column.getDataType())
                ? toBsonType(column.getDataType()) : existingType;
        if (baseType == null) {
            throw new DbAccessException("MongoDB 已有字段缺少 bsonType，更新时必须提供 dataType: "
                    + column.getColumnName());
        }
        if (column.getNullable() != null) {
            property.put("bsonType", applyNullability(baseType, column.getNullable()));
        } else if (StringUtils.hasText(column.getDataType())) {
            property.put("bsonType", applyNullability(baseType, allowsNull(existing)));
        }
        if (column.getColumnLength() != null) {
            String type = readType(property);
            if (!"STRING".equals(type)) {
                throw new DbAccessException("MongoDB 仅字符串类型支持 columnLength");
            }
            if (column.getColumnLength() <= 0) {
                throw new DbAccessException("MongoDB columnLength 必须大于 0");
            }
            property.put("maxLength", column.getColumnLength());
        }
        if (column.getColumnComment() != null) {
            if (StringUtils.hasText(column.getColumnComment())) {
                property.put("description", column.getColumnComment().trim());
            } else {
                property.remove("description");
            }
        }
        return property;
    }

    private static Object applyNullability(Object baseType, boolean nullable) {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        if (baseType instanceof Collection<?> collection) {
            collection.forEach(item -> {
                if (!"null".equals(String.valueOf(item))) {
                    types.add(String.valueOf(item));
                }
            });
        } else {
            types.add(String.valueOf(baseType));
        }
        if (nullable) {
            types.add("null");
        }
        return types.size() == 1 ? types.iterator().next() : new ArrayList<>(types);
    }

    private static void validateColumn(DbTableColumnDefinition column, boolean requireDataType) throws DbAccessException {
        if (column == null) {
            throw new DbAccessException("MongoDB 字段定义不能为空");
        }
        validateFieldName(column.getColumnName());
        if (requireDataType || StringUtils.hasText(column.getDataType())) {
            toBsonType(column.getDataType());
        }
        if (Boolean.TRUE.equals(column.getAutoIncrement())) {
            throw new DbAccessException("MongoDB 不支持 autoIncrement");
        }
        if (column.getDefaultValue() != null) {
            throw new DbAccessException("MongoDB validator 不会应用 defaultValue");
        }
        if (Boolean.TRUE.equals(column.getPrimaryKey()) && !"_id".equals(column.getColumnName().trim())) {
            throw new DbAccessException("MongoDB 仅 _id 可作为主键");
        }
        if (column.getColumnPrecision() != null || column.getColumnScale() != null) {
            throw new DbAccessException("MongoDB validator 不支持 columnPrecision/columnScale");
        }
    }

    private static Document directSchema(Document validator, boolean create) throws DbAccessException {
        Object value = validator.get(JSON_SCHEMA);
        if (value instanceof Document schema) {
            return schema;
        }
        if (value != null) {
            throw new DbAccessException("MongoDB validator.$jsonSchema 必须是对象");
        }
        if (containsNestedJsonSchema(validator)) {
            throw new DbAccessException("MongoDB 复杂 validator 中的 $jsonSchema 无法安全修改");
        }
        if (!create) {
            return new Document();
        }
        Document schema = new Document();
        validator.put(JSON_SCHEMA, schema);
        return schema;
    }

    private static boolean containsNestedJsonSchema(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (JSON_SCHEMA.equals(String.valueOf(entry.getKey())) || containsNestedJsonSchema(entry.getValue())) {
                    return true;
                }
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (containsNestedJsonSchema(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static LinkedHashSet<String> requiredFields(Document schema) {
        LinkedHashSet<String> required = new LinkedHashSet<>();
        Object value = schema.get("required");
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> required.add(String.valueOf(item)));
        }
        return required;
    }

    private static String readType(Document definition) {
        Object value = definition.get("bsonType");
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (!"null".equals(String.valueOf(item))) {
                    return fromBsonType(String.valueOf(item));
                }
            }
            return "NULL";
        }
        return value == null ? "OBJECT" : fromBsonType(String.valueOf(value));
    }

    private static boolean allowsNull(Document definition) {
        return definition.get("bsonType") instanceof Collection<?> types
                && types.stream().anyMatch(type -> "null".equals(String.valueOf(type)));
    }

    private static String fromBsonType(String bsonType) {
        return switch (bsonType) {
            case "string" -> "STRING";
            case "int" -> "INT32";
            case "long" -> "INT64";
            case "double" -> "DOUBLE";
            case "decimal" -> "DECIMAL128";
            case "bool" -> "BOOLEAN";
            case "date" -> "DATE_TIME";
            case "objectId" -> "OBJECT_ID";
            case "object" -> "OBJECT";
            case "array" -> "ARRAY";
            case "binData" -> "BINARY";
            case "regex" -> "REGEX";
            default -> bsonType.toUpperCase(Locale.ROOT);
        };
    }

    private static int jdbcTypeForSchema(String type) {
        return switch (type) {
            case "STRING", "OBJECT_ID", "REGEX" -> Types.VARCHAR;
            case "INT32" -> Types.INTEGER;
            case "INT64" -> Types.BIGINT;
            case "DOUBLE" -> Types.DOUBLE;
            case "DECIMAL128" -> Types.DECIMAL;
            case "BOOLEAN" -> Types.BOOLEAN;
            case "DATE_TIME" -> Types.TIMESTAMP;
            case "BINARY" -> Types.BINARY;
            case "ARRAY" -> Types.ARRAY;
            default -> Types.JAVA_OBJECT;
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T deepCopy(T value) {
        if (value instanceof Document document) {
            Document copy = new Document();
            document.forEach((key, item) -> copy.put(key, deepCopy(item)));
            return (T) copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(deepCopy(item)));
            return (T) copy;
        }
        return value;
    }

    private static Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record RemoveResult(Document validator, boolean changed) {
    }
}
