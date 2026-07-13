package ai.platform.aiassit.db.engine.executor.mongodb.support;

import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import org.bson.Document;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 解析并校验由兼容 {@code sql} 字段承载的 MongoDB JSON 命令信封。 */
public final class MongoCommandEnvelope {

    private static final Set<String> DANGEROUS_KEYS = Set.of("$where", "$function", "$accumulator");
    private static final Set<String> QUERY_PIPELINE_STAGES = Set.of(
            "$addFields", "$bucket", "$bucketAuto", "$count", "$densify", "$documents", "$fill",
            "$group", "$limit", "$match", "$project", "$redact", "$replaceRoot", "$replaceWith",
            "$sample", "$set", "$setWindowFields", "$skip", "$sort", "$sortByCount", "$unset", "$unwind"
    );
    private static final Set<String> UPDATE_OPERATORS = Set.of(
            "$set", "$unset", "$inc", "$mul", "$rename", "$min", "$max", "$currentDate",
            "$setOnInsert", "$push", "$addToSet", "$pop", "$pull", "$pullAll", "$bit"
    );

    private MongoCommandEnvelope() {
    }

    public static QueryCommand parseQuery(String json, List<Object> parameters) throws DbAccessException {
        Document root = parseRoot(json, parameters);
        String operation = requiredText(root, "operation").toLowerCase(Locale.ROOT);
        String collection = requireCollection(requiredText(root, "collection"));
        if ("find".equals(operation)) {
            rejectUnknown(root, Set.of("operation", "collection", "filter", "projection", "sort", "skip", "limit"));
            Document filter = document(root, "filter", true);
            if (filter == null) {
                filter = new Document();
            }
            Document projection = document(root, "projection", true);
            Document sort = document(root, "sort", true);
            rejectDangerous(filter);
            rejectDangerous(projection);
            rejectDangerous(sort);
            return new QueryCommand(operation, collection, filter, projection, sort,
                    nonNegativeInteger(root, "skip"), positiveInteger(root, "limit"), List.of());
        }
        if ("aggregate".equals(operation)) {
            rejectUnknown(root, Set.of("operation", "collection", "pipeline", "limit"));
            List<Document> pipeline = documentList(root, "pipeline", true);
            for (Document stage : pipeline) {
                if (stage.size() != 1) {
                    throw new DbAccessException("MongoDB 聚合阶段必须且只能包含一个操作符");
                }
                String stageName = stage.keySet().iterator().next();
                if (!QUERY_PIPELINE_STAGES.contains(stageName)) {
                    throw new DbAccessException("MongoDB 查询不允许聚合阶段: " + stageName);
                }
                rejectDangerous(stage);
            }
            return new QueryCommand(operation, collection, new Document(), null, null,
                    null, positiveInteger(root, "limit"), pipeline);
        }
        throw new DbAccessException("MongoDB query 仅支持 find/aggregate");
    }

    public static ExecuteCommand parseExecute(String json, List<Object> parameters) throws DbAccessException {
        Document root = parseRoot(json, parameters);
        String operation = requiredText(root, "operation");
        String collection = requireCollection(requiredText(root, "collection"));
        return switch (operation) {
            case "insertOne" -> {
                rejectUnknown(root, Set.of("operation", "collection", "document"));
                Document document = document(root, "document", false);
                rejectDangerous(document);
                yield new ExecuteCommand(operation, collection, null, document, List.of(), null, null, false);
            }
            case "insertMany" -> {
                rejectUnknown(root, Set.of("operation", "collection", "documents"));
                List<Document> documents = documentList(root, "documents", false);
                if (documents.isEmpty()) {
                    throw new DbAccessException("MongoDB insertMany documents 不能为空");
                }
                for (Document document : documents) {
                    rejectDangerous(document);
                }
                yield new ExecuteCommand(operation, collection, null, null, documents, null, null, false);
            }
            case "updateOne" -> {
                rejectUnknown(root, Set.of("operation", "collection", "filter", "update", "upsert"));
                Document filter = document(root, "filter", false);
                Document update = document(root, "update", false);
                rejectDangerous(filter);
                requireNonEmptyFilter(filter, operation, false);
                validateUpdate(update);
                yield new ExecuteCommand(operation, collection, filter, null, List.of(), update, null,
                        booleanValue(root, "upsert", false));
            }
            case "updateMany" -> {
                rejectUnknown(root, Set.of(
                        "operation", "collection", "filter", "update", "upsert", "allowAllDocuments"));
                Document filter = document(root, "filter", false);
                Document update = document(root, "update", false);
                rejectDangerous(filter);
                requireNonEmptyFilter(filter, operation, booleanValue(root, "allowAllDocuments", false));
                validateUpdate(update);
                yield new ExecuteCommand(operation, collection, filter, null, List.of(), update, null,
                        booleanValue(root, "upsert", false));
            }
            case "replaceOne" -> {
                rejectUnknown(root, Set.of("operation", "collection", "filter", "replacement", "upsert"));
                Document filter = document(root, "filter", false);
                Document replacement = document(root, "replacement", false);
                rejectDangerous(filter);
                rejectDangerous(replacement);
                requireNonEmptyFilter(filter, operation, false);
                if (replacement.keySet().stream().anyMatch(key -> key.startsWith("$"))) {
                    throw new DbAccessException("MongoDB replacement 不允许包含顶层操作符");
                }
                yield new ExecuteCommand(operation, collection, filter, null, List.of(), null, replacement,
                        booleanValue(root, "upsert", false));
            }
            case "deleteOne" -> {
                rejectUnknown(root, Set.of("operation", "collection", "filter"));
                Document filter = document(root, "filter", false);
                rejectDangerous(filter);
                requireNonEmptyFilter(filter, operation, false);
                yield new ExecuteCommand(operation, collection, filter, null, List.of(), null, null, false);
            }
            case "deleteMany" -> {
                rejectUnknown(root, Set.of("operation", "collection", "filter", "allowAllDocuments"));
                Document filter = document(root, "filter", false);
                rejectDangerous(filter);
                requireNonEmptyFilter(filter, operation, booleanValue(root, "allowAllDocuments", false));
                yield new ExecuteCommand(operation, collection, filter, null, List.of(), null, null, false);
            }
            default -> throw new DbAccessException("MongoDB execute 不支持操作: " + operation);
        };
    }

    public static String requireCollection(String collection) throws DbAccessException {
        if (!StringUtils.hasText(collection)) {
            throw new DbAccessException("MongoDB collection 名称不合法");
        }
        String normalized = collection.trim();
        if (normalized.indexOf('\0') >= 0 || normalized.startsWith("system.")) {
            throw new DbAccessException("MongoDB collection 名称不合法");
        }
        return normalized;
    }

    private static Document parseRoot(String json, List<Object> parameters) throws DbAccessException {
        if (!StringUtils.hasText(json)) {
            throw new DbAccessException("MongoDB JSON 命令不能为空");
        }
        Document parsed;
        try {
            parsed = Document.parse(json.trim());
        } catch (RuntimeException ex) {
            throw new DbAccessException("MongoDB JSON 命令格式错误", ex);
        }
        List<Object> values = parameters == null ? List.of() : parameters;
        boolean[] used = new boolean[values.size()];
        Object resolved = replaceParameters(parsed, values, used);
        for (int index = 0; index < used.length; index++) {
            if (!used[index]) {
                throw new DbAccessException("MongoDB 命令参数未被使用: " + index);
            }
        }
        return (Document) resolved;
    }

    private static Object replaceParameters(Object value, List<Object> parameters, boolean[] used) throws DbAccessException {
        if (value instanceof Document document) {
            if (document.containsKey("$param")) {
                if (document.size() != 1 || !(document.get("$param") instanceof Number number)
                        || number.doubleValue() != number.longValue()) {
                    throw new DbAccessException("MongoDB $param 占位符格式必须为 {\"$param\": n}");
                }
                int index = number.intValue();
                if (index < 0 || index >= parameters.size()) {
                    throw new DbAccessException("MongoDB $param 下标越界: " + index);
                }
                used[index] = true;
                return normalizeParameter(parameters.get(index));
            }
            Document copy = new Document();
            for (Map.Entry<String, Object> entry : document.entrySet()) {
                copy.put(entry.getKey(), replaceParameters(entry.getValue(), parameters, used));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(replaceParameters(item, parameters, used));
            }
            return copy;
        }
        return value;
    }

    private static Object normalizeParameter(Object value) throws DbAccessException {
        if (value instanceof Map<?, ?> map) {
            Document document = new Document();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new DbAccessException("MongoDB 参数对象的 key 必须是字符串");
                }
                document.put(key, normalizeParameter(entry.getValue()));
            }
            return document;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(normalizeParameter(item));
            }
            return normalized;
        }
        return value;
    }

    private static void validateUpdate(Document update) throws DbAccessException {
        if (update.isEmpty()) {
            throw new DbAccessException("MongoDB update 不能为空");
        }
        for (String operator : update.keySet()) {
            if (!UPDATE_OPERATORS.contains(operator)) {
                throw new DbAccessException("MongoDB update 操作符不允许: " + operator);
            }
        }
        rejectDangerous(update);
    }

    private static void requireNonEmptyFilter(Document filter, String operation, boolean allowAllDocuments)
            throws DbAccessException {
        if (filter.isEmpty() && !allowAllDocuments) {
            throw new DbAccessException("MongoDB " + operation
                    + " 空过滤器需要显式设置 allowAllDocuments=true");
        }
    }

    private static void rejectDangerous(Object value) throws DbAccessException {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (DANGEROUS_KEYS.contains(key)) {
                    throw new DbAccessException("MongoDB 命令禁止服务器端脚本操作: " + key);
                }
                rejectDangerous(entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                rejectDangerous(item);
            }
        }
    }

    private static void rejectUnknown(Document root, Set<String> allowed) throws DbAccessException {
        Set<String> unknown = new LinkedHashSet<>(root.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new DbAccessException("MongoDB 命令包含未知字段: " + unknown);
        }
    }

    private static String requiredText(Document root, String name) throws DbAccessException {
        Object value = root.get(name);
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            throw new DbAccessException("MongoDB 命令字段 " + name + " 不能为空");
        }
        return text.trim();
    }

    private static Document document(Document root, String name, boolean optional) throws DbAccessException {
        if (!root.containsKey(name) && optional) {
            return null;
        }
        if (!root.containsKey(name)) {
            throw new DbAccessException("MongoDB 命令字段 " + name + " 不能为空");
        }
        Object value = root.get(name);
        if (!(value instanceof Document document)) {
            throw new DbAccessException("MongoDB 命令字段 " + name + " 必须是对象");
        }
        return document;
    }

    private static List<Document> documentList(Document root, String name, boolean allowEmpty) throws DbAccessException {
        Object value = root.get(name);
        if (!(value instanceof List<?> list)) {
            throw new DbAccessException("MongoDB 命令字段 " + name + " 必须是数组");
        }
        List<Document> documents = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Document document)) {
                throw new DbAccessException("MongoDB 命令字段 " + name + " 的元素必须是对象");
            }
            documents.add(document);
        }
        if (!allowEmpty && documents.isEmpty()) {
            throw new DbAccessException("MongoDB 命令字段 " + name + " 不能为空");
        }
        return documents;
    }

    private static Integer nonNegativeInteger(Document root, String name) throws DbAccessException {
        Object value = root.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)
                || number.doubleValue() != number.longValue()
                || number.longValue() < 0
                || number.longValue() > Integer.MAX_VALUE) {
            throw new DbAccessException("MongoDB 命令字段 " + name + " 必须是非负整数");
        }
        return number.intValue();
    }

    private static Integer positiveInteger(Document root, String name) throws DbAccessException {
        Integer value = nonNegativeInteger(root, name);
        if (value != null && value <= 0) {
            throw new DbAccessException("MongoDB 命令字段 " + name + " 必须是正整数");
        }
        return value;
    }

    private static boolean booleanValue(Document root, String name, boolean defaultValue) throws DbAccessException {
        Object value = root.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean bool)) {
            throw new DbAccessException("MongoDB 命令字段 " + name + " 必须是布尔值");
        }
        return bool;
    }

    public record QueryCommand(
            String operation,
            String collection,
            Document filter,
            Document projection,
            Document sort,
            Integer skip,
            Integer limit,
            List<Document> pipeline
    ) {
    }

    public record ExecuteCommand(
            String operation,
            String collection,
            Document filter,
            Document document,
            List<Document> documents,
            Document update,
            Document replacement,
            boolean upsert
    ) {
    }
}
