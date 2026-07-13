package ai.platform.aiassit.db.engine.executor.mongodb.provider;

import ai.platform.aiassit.db.engine.executor.mongodb.support.MongoBsonSupport;
import ai.platform.aiassit.db.engine.executor.mongodb.support.MongoCommandEnvelope;
import ai.platform.aiassit.db.engine.executor.mongodb.support.MongoConnectionSupport;
import ai.platform.aiassit.db.engine.executor.mongodb.support.MongoSchemaSupport;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.model.DbColumnMeta;
import ai.platform.aiassit.db.engine.executor.spi.model.DbIndexMeta;
import ai.platform.aiassit.db.engine.executor.spi.model.DbQueryColumn;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableColumnDefinition;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableMeta;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbAccessExecutor;
import ai.platform.aiassit.db.engine.executor.spi.request.DeleteTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ExecuteRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableIndexesRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTablesRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.QueryRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.SaveTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.SaveTableRequest;
import ai.platform.aiassit.db.engine.executor.spi.result.DeleteTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ExecuteResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ListTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ListTableIndexesResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ListTablesResult;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;
import ai.platform.aiassit.db.engine.executor.spi.result.SaveTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.SaveTableResult;
import ai.platform.aiassit.db.engine.executor.spi.result.TestConnectionResult;
import com.mongodb.MongoException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.ValidationOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MongoDbAccessExecutor implements DbAccessExecutor {

    private static final int DEFAULT_SCHEMA_SAMPLE_SIZE = 100;
    private static final int MAX_SCHEMA_SAMPLE_SIZE = 1_000;
    private static final int DEFAULT_QUERY_LIMIT = 1_000;

    private final MongoConnectionSupport connectionSupport;
    private final DbAccessContext context;

    public MongoDbAccessExecutor(MongoConnectionSupport connectionSupport, DbAccessContext context) {
        this.connectionSupport = connectionSupport;
        this.context = context;
    }

    @Override
    public TestConnectionResult testConnection() throws DbAccessException {
        String databaseName = connectionSupport.databaseName(context, null);
        try {
            MongoDatabase database = connectionSupport.database(context, null);
            database.runCommand(new Document("ping", 1));
            String version = null;
            try {
                version = database.runCommand(new Document("buildInfo", 1)).getString("version");
            } catch (MongoException ignored) {
                // buildInfo 可能未授权；ping 成功仍表示连接可用。
            }
            return TestConnectionResult.builder()
                    .success(Boolean.TRUE)
                    .message("连接成功")
                    .databaseProductName("MongoDB")
                    .databaseProductVersion(version)
                    .catalog(databaseName)
                    .schema(null)
                    .build();
        } catch (MongoException ex) {
            throw new DbAccessException("MongoDB 连接测试失败", ex);
        }
    }

    @Override
    public ListTablesResult listTables(ListTablesRequest request) throws DbAccessException {
        String requestedDatabase = request == null ? null : request.getSchemaName();
        String keyword = request == null ? null : request.getKeyword();
        Integer limit = request == null ? null : request.getLimit();
        try {
            MongoDatabase database = connectionSupport.database(context, requestedDatabase);
            List<DbTableMeta> tables = new ArrayList<>();
            for (Document info : database.listCollections()) {
                String name = info.getString("name");
                if (!StringUtils.hasText(name) || name.startsWith("system.")) {
                    continue;
                }
                if (StringUtils.hasText(keyword)
                        && !name.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                Document validator = validator(info);
                tables.add(DbTableMeta.builder()
                        .tableName(name)
                        .tableComment(MongoSchemaSupport.tableComment(validator))
                        .tableType(collectionType(info))
                        .build());
            }
            tables.sort(Comparator.comparing(DbTableMeta::getTableName));
            if (limit != null && limit > 0 && tables.size() > limit) {
                tables = new ArrayList<>(tables.subList(0, limit));
            }
            return ListTablesResult.builder().tables(tables).build();
        } catch (MongoException ex) {
            throw new DbAccessException("查询 MongoDB collection 失败", ex);
        }
    }

    @Override
    public ListTableColumnsResult listTableColumns(ListTableColumnsRequest request) throws DbAccessException {
        String tableName = requiredTable(request == null ? null : request.getTableName());
        try {
            MongoDatabase database = connectionSupport.database(context, request.getSchemaName());
            Document info = requireCollection(database, tableName);
            Document validator = validator(info);
            List<DbColumnMeta> defined = MongoSchemaSupport.columnsFromValidator(tableName, validator);
            LinkedHashMap<String, DbColumnMeta> columns = new LinkedHashMap<>();
            DbColumnMeta declaredId = null;
            for (DbColumnMeta column : defined) {
                if ("_id".equals(column.getColumnName())) {
                    declaredId = column;
                } else {
                    columns.put(column.getColumnName(), column);
                }
            }
            if (!"VIEW".equals(collectionType(info))) {
                columns.put("_id", declaredId == null ? DbColumnMeta.builder()
                        .tableName(tableName).columnName("_id").dataType("OBJECT_ID")
                        .nullable(false).primaryKey(true).build() : declaredId);
            }

            int sampleSize = schemaSampleSize();
            List<Document> samples = new ArrayList<>();
            if (sampleSize > 0) {
                for (Document document : database.getCollection(tableName).find().limit(sampleSize)) {
                    samples.add(document);
                }
            }
            Map<String, FieldInference> inferred = inferFields(samples);
            List<String> inferredNames = inferred.keySet().stream().sorted().toList();
            for (String name : inferredNames) {
                if (columns.containsKey(name)) {
                    continue;
                }
                FieldInference value = inferred.get(name);
                columns.put(name, DbColumnMeta.builder()
                        .tableName(tableName)
                        .columnName(name)
                        .dataType(value.typeName())
                        .nullable(value.nullSeen || value.seen < samples.size())
                        .primaryKey("_id".equals(name))
                        .build());
            }

            List<DbColumnMeta> result = new ArrayList<>();
            DbColumnMeta id = columns.remove("_id");
            if (id != null) {
                result.add(id);
            }
            result.addAll(columns.values());
            for (int index = 0; index < result.size(); index++) {
                result.get(index).setOrdinalPosition(index + 1);
            }
            return ListTableColumnsResult.builder().tableName(tableName).columns(result).build();
        } catch (MongoException ex) {
            throw new DbAccessException("查询 MongoDB 字段定义失败", ex);
        }
    }

    @Override
    public ListTableIndexesResult listTableIndexes(ListTableIndexesRequest request) throws DbAccessException {
        String tableName = requiredTable(request == null ? null : request.getTableName());
        try {
            MongoDatabase database = connectionSupport.database(context, request.getSchemaName());
            Document info = requireCollection(database, tableName);
            if ("VIEW".equals(collectionType(info))) {
                return ListTableIndexesResult.builder().tableName(tableName).indexes(List.of()).build();
            }
            List<DbIndexMeta> indexes = new ArrayList<>();
            for (Document index : database.getCollection(tableName).listIndexes()) {
                String indexName = index.getString("name");
                boolean primary = "_id_".equals(indexName);
                boolean unique = Boolean.TRUE.equals(index.getBoolean("unique", false)) || primary;
                Document key = index.get("key", Document.class);
                Document weights = index.get("weights", Document.class);
                if (key != null && key.containsKey("_fts") && weights != null) {
                    int order = 1;
                    for (String field : weights.keySet()) {
                        indexes.add(indexMeta(tableName, indexName, "TEXT", unique, primary, field, order++));
                    }
                    continue;
                }
                if (key == null) {
                    continue;
                }
                int order = 1;
                for (Map.Entry<String, Object> entry : key.entrySet()) {
                    indexes.add(indexMeta(tableName, indexName, indexType(entry.getValue()), unique, primary,
                            entry.getKey(), order++));
                }
            }
            return ListTableIndexesResult.builder().tableName(tableName).indexes(indexes).build();
        } catch (MongoException ex) {
            throw new DbAccessException("查询 MongoDB 索引定义失败", ex);
        }
    }

    @Override
    public QueryResult query(QueryRequest request) throws DbAccessException {
        if (request == null) {
            throw new DbAccessException("MongoDB 查询请求不能为空");
        }
        MongoCommandEnvelope.QueryCommand command = MongoCommandEnvelope.parseQuery(request.getSql(), request.getParameters());
        Instant started = Instant.now();
        try {
            MongoDatabase database = connectionSupport.database(context, null);
            Document collectionInfo = requireCollection(database, command.collection());
            MongoCollection<Document> collection = database.getCollection(command.collection());
            Integer maxRows = effectiveLimit(command.limit(), request.getMaxRows());
            List<Document> rawRows = new ArrayList<>();
            if ("find".equals(command.operation())) {
                FindIterable<Document> iterable = collection.find(command.filter());
                if (command.projection() != null) {
                    iterable = iterable.projection(command.projection());
                }
                if (command.sort() != null) {
                    iterable = iterable.sort(command.sort());
                }
                if (command.skip() != null) {
                    iterable = iterable.skip(command.skip());
                }
                if (maxRows != null) {
                    iterable = iterable.limit(maxRows);
                }
                iterable.into(rawRows);
            } else {
                AggregateIterable<Document> iterable = collection.aggregate(command.pipeline());
                try (MongoCursor<Document> cursor = iterable.iterator()) {
                    while (cursor.hasNext() && rawRows.size() < maxRows) {
                        rawRows.add(cursor.next());
                    }
                }
            }
            List<DbQueryColumn> columns = MongoBsonSupport.queryColumns(rawRows);
            if (columns.isEmpty()) {
                columns = MongoSchemaSupport.queryColumnsFromValidator(validator(collectionInfo));
            }
            List<Map<String, Object>> rows = rawRows.stream().map(MongoBsonSupport::toSafeDocument).toList();
            return QueryResult.builder()
                    .columns(columns)
                    .rows(rows)
                    .rowCount(rows.size())
                    .executionMs(Duration.between(started, Instant.now()).toMillis())
                    .build();
        } catch (MongoException ex) {
            throw new DbAccessException("执行 MongoDB 查询失败", ex);
        }
    }

    @Override
    public ExecuteResult execute(ExecuteRequest request) throws DbAccessException {
        if (request == null) {
            throw new DbAccessException("MongoDB 执行请求不能为空");
        }
        MongoCommandEnvelope.ExecuteCommand command = MongoCommandEnvelope.parseExecute(request.getSql(), request.getParameters());
        Instant started = Instant.now();
        try {
            MongoDatabase database = connectionSupport.database(context, null);
            Document collectionInfo = requireCollection(database, command.collection());
            requireMutableCollection(collectionInfo, command.collection());
            MongoCollection<Document> collection = database.getCollection(command.collection());
            long affected = switch (command.operation()) {
                case "insertOne" -> {
                    requireAcknowledged(collection.insertOne(command.document()));
                    yield 1L;
                }
                case "insertMany" -> {
                    requireAcknowledged(collection.insertMany(command.documents()));
                    yield command.documents().size();
                }
                case "updateOne" -> updateAffected(collection.updateOne(command.filter(), command.update(),
                        new UpdateOptions().upsert(command.upsert())));
                case "updateMany" -> updateAffected(collection.updateMany(command.filter(), command.update(),
                        new UpdateOptions().upsert(command.upsert())));
                case "replaceOne" -> updateAffected(collection.replaceOne(command.filter(), command.replacement(),
                        new ReplaceOptions().upsert(command.upsert())));
                case "deleteOne" -> deleteAffected(collection.deleteOne(command.filter()));
                case "deleteMany" -> deleteAffected(collection.deleteMany(command.filter()));
                default -> throw new DbAccessException("MongoDB execute 不支持操作: " + command.operation());
            };
            return ExecuteResult.builder()
                    .affectedRows(toIntegerCount(affected, "affectedRows"))
                    .executionMs(Duration.between(started, Instant.now()).toMillis())
                    .build();
        } catch (MongoException ex) {
            throw new DbAccessException("执行 MongoDB 写操作失败", ex);
        }
    }

    @Override
    public SaveTableResult saveTable(SaveTableRequest request) throws DbAccessException {
        String tableName = requiredTable(request == null ? null : request.getTableName());
        try {
            MongoDatabase database = connectionSupport.database(context, request.getSchemaName());
            Document info = collectionInfo(database, tableName);
            List<String> commands = new ArrayList<>();
            if (info == null) {
                Document validator = buildRequestedValidator(request.getColumns(), request.getTableComment());
                if (validator.isEmpty()) {
                    database.createCollection(tableName);
                } else {
                    database.createCollection(tableName, new CreateCollectionOptions()
                            .validationOptions(new ValidationOptions().validator(validator)));
                }
                Document command = new Document("create", tableName);
                if (!validator.isEmpty()) {
                    command.put("validator", validator);
                }
                commands.add(command.toJson());
                return SaveTableResult.builder().tableName(tableName).created(true).updated(false)
                        .executedSqls(commands).build();
            }
            requireMutableCollection(info, tableName);
            boolean requestedUpdate = StringUtils.hasText(request.getTableComment())
                    || !CollectionUtils.isEmpty(request.getColumns());
            if (requestedUpdate) {
                Document merged = MongoSchemaSupport.mergeColumns(
                        validator(info), request.getColumns(), request.getTableComment());
                Document command = new Document("collMod", tableName).append("validator", merged);
                database.runCommand(command);
                commands.add(command.toJson());
            }
            return SaveTableResult.builder().tableName(tableName).created(false).updated(requestedUpdate)
                    .executedSqls(commands).build();
        } catch (MongoException ex) {
            throw new DbAccessException("保存 MongoDB collection 定义失败", ex);
        }
    }

    @Override
    public SaveTableColumnsResult saveTableColumns(SaveTableColumnsRequest request) throws DbAccessException {
        String tableName = requiredTable(request == null ? null : request.getTableName());
        if (CollectionUtils.isEmpty(request.getColumns())) {
            return SaveTableColumnsResult.builder().tableName(tableName).affectedColumnCount(0)
                    .executedSqls(List.of()).build();
        }
        try {
            MongoDatabase database = connectionSupport.database(context, request.getSchemaName());
            Document info = requireCollection(database, tableName);
            requireMutableCollection(info, tableName);
            Document merged = MongoSchemaSupport.mergeColumns(validator(info), request.getColumns(), null);
            Document command = new Document("collMod", tableName).append("validator", merged);
            database.runCommand(command);
            return SaveTableColumnsResult.builder()
                    .tableName(tableName)
                    .affectedColumnCount(request.getColumns().size())
                    .executedSqls(List.of(command.toJson()))
                    .build();
        } catch (MongoException ex) {
            throw new DbAccessException("保存 MongoDB 字段定义失败", ex);
        }
    }

    @Override
    public DeleteTableColumnsResult deleteTableColumns(DeleteTableColumnsRequest request) throws DbAccessException {
        String tableName = requiredTable(request == null ? null : request.getTableName());
        if (CollectionUtils.isEmpty(request.getColumnNames())) {
            throw new DbAccessException("columnNames 不能为空");
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String value : request.getColumnNames()) {
            MongoSchemaSupport.validateFieldName(value);
            String name = value.trim();
            if ("_id".equals(name)) {
                throw new DbAccessException("MongoDB _id 字段不能删除");
            }
            names.add(name);
        }
        List<String> commands = new ArrayList<>();
        try {
            MongoDatabase database = connectionSupport.database(context, request.getSchemaName());
            Document info = requireCollection(database, tableName);
            requireMutableCollection(info, tableName);
            MongoCollection<Document> collection = database.getCollection(tableName);
            Document existingValidator = validator(info);
            for (String name : names) {
                boolean inValidator = MongoSchemaSupport.definesField(existingValidator, name);
                boolean inData = collection.find(new Document(name, new Document("$exists", true)))
                        .projection(new Document("_id", 1)).limit(1).first() != null;
                if (!inValidator && !inData) {
                    throw new DbAccessException("MongoDB 字段不存在: " + name);
                }
            }
            MongoSchemaSupport.RemoveResult removal = MongoSchemaSupport.removeColumns(existingValidator, names);
            if (removal.changed()) {
                Document collMod = new Document("collMod", tableName).append("validator", removal.validator());
                database.runCommand(collMod);
                commands.add(collMod.toJson());
            }
            Document unset = new Document();
            names.forEach(name -> unset.put(name, ""));
            UpdateResult unsetResult = collection.updateMany(new Document(), new Document("$unset", unset));
            if (!unsetResult.wasAcknowledged()) {
                throw new DbAccessException("MongoDB 字段删除未得到服务端确认");
            }
            Document updateCommand = new Document("update", tableName)
                    .append("updates", List.of(new Document("q", new Document())
                            .append("u", new Document("$unset", unset)).append("multi", true)));
            commands.add(updateCommand.toJson());
            return DeleteTableColumnsResult.builder()
                    .tableName(tableName)
                    .affectedColumnCount(names.size())
                    .executedSqls(commands)
                    .build();
        } catch (MongoException ex) {
            String partial = commands.isEmpty() ? "" : "，失败前已执行: " + commands;
            throw new DbAccessException("删除 MongoDB 字段失败" + partial, ex);
        }
    }

    private Document buildRequestedValidator(List<DbTableColumnDefinition> columns, String comment)
            throws DbAccessException {
        if (CollectionUtils.isEmpty(columns) && !StringUtils.hasText(comment)) {
            return new Document();
        }
        return MongoSchemaSupport.mergeColumns(new Document(), columns, comment);
    }

    private Document requireCollection(MongoDatabase database, String tableName) throws DbAccessException {
        Document info = collectionInfo(database, tableName);
        if (info == null) {
            throw new DbAccessException("MongoDB collection 不存在: " + tableName);
        }
        return info;
    }

    private Document collectionInfo(MongoDatabase database, String tableName) {
        return database.listCollections().filter(new Document("name", tableName)).first();
    }

    private void requireMutableCollection(Document info, String tableName) throws DbAccessException {
        String type = collectionType(info);
        if ("VIEW".equals(type) || "TIMESERIES".equals(type)) {
            throw new DbAccessException("MongoDB " + type + " 暂不支持结构或数据写入: " + tableName);
        }
    }

    private Document validator(Document info) {
        if (info == null || !(info.get("options") instanceof Document options)
                || !(options.get("validator") instanceof Document validator)) {
            return new Document();
        }
        return validator;
    }

    private String collectionType(Document info) {
        String type = info == null ? null : info.getString("type");
        if ("view".equalsIgnoreCase(type)) {
            return "VIEW";
        }
        Document options = info == null ? null : info.get("options", Document.class);
        if (options != null && options.containsKey("timeseries")) {
            return "TIMESERIES";
        }
        return "COLLECTION";
    }

    private String requiredTable(String tableName) throws DbAccessException {
        return MongoCommandEnvelope.requireCollection(tableName);
    }

    private int schemaSampleSize() throws DbAccessException {
        Object value = context.getAttributes() == null ? null : context.getAttributes().get("schemaInferenceSampleSize");
        if (value == null) {
            return DEFAULT_SCHEMA_SAMPLE_SIZE;
        }
        int parsed;
        try {
            parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new DbAccessException("schemaInferenceSampleSize 必须是整数", ex);
        }
        return Math.max(0, Math.min(parsed, MAX_SCHEMA_SAMPLE_SIZE));
    }

    private Map<String, FieldInference> inferFields(List<Document> samples) {
        Map<String, FieldInference> fields = new LinkedHashMap<>();
        for (Document sample : samples) {
            for (Map.Entry<String, Object> entry : sample.entrySet()) {
                fields.computeIfAbsent(entry.getKey(), key -> new FieldInference()).observe(entry.getValue());
            }
        }
        return fields;
    }

    private DbIndexMeta indexMeta(
            String tableName,
            String indexName,
            String indexType,
            boolean unique,
            boolean primary,
            String columnName,
            int order
    ) {
        return DbIndexMeta.builder().tableName(tableName).indexName(indexName).indexType(indexType)
                .uniqueFlag(unique).primaryFlag(primary).columnName(columnName).columnOrder(order).build();
    }

    private String indexType(Object direction) {
        if (direction instanceof Number number) {
            return number.intValue() < 0 ? "DESC" : "ASC";
        }
        String type = String.valueOf(direction).toUpperCase(Locale.ROOT);
        return switch (type) {
            case "1" -> "ASC";
            case "-1" -> "DESC";
            case "2DSPHERE" -> "2DSPHERE";
            case "2D" -> "2D";
            case "HASHED" -> "HASHED";
            case "TEXT" -> "TEXT";
            default -> type;
        };
    }

    private Integer effectiveLimit(Integer commandLimit, Integer requestLimit) {
        Integer left = commandLimit != null && commandLimit > 0 ? commandLimit : null;
        Integer right = requestLimit != null && requestLimit > 0 ? requestLimit : null;
        if (left == null) {
            return right == null ? DEFAULT_QUERY_LIMIT : right;
        }
        return right == null ? left : Math.min(left, right);
    }

    private long updateAffected(UpdateResult result) throws DbAccessException {
        if (!result.wasAcknowledged()) {
            throw new DbAccessException("MongoDB 写操作未得到服务端确认");
        }
        return result.getUpsertedId() == null ? result.getModifiedCount() : Math.max(1L, result.getModifiedCount());
    }

    private long deleteAffected(DeleteResult result) throws DbAccessException {
        if (!result.wasAcknowledged()) {
            throw new DbAccessException("MongoDB 删除操作未得到服务端确认");
        }
        return result.getDeletedCount();
    }

    private void requireAcknowledged(InsertOneResult result) throws DbAccessException {
        if (!result.wasAcknowledged()) {
            throw new DbAccessException("MongoDB 插入操作未得到服务端确认");
        }
    }

    private void requireAcknowledged(InsertManyResult result) throws DbAccessException {
        if (!result.wasAcknowledged()) {
            throw new DbAccessException("MongoDB 批量插入未得到服务端确认");
        }
    }

    private Integer toIntegerCount(long value, String label) throws DbAccessException {
        if (value > Integer.MAX_VALUE) {
            throw new DbAccessException("MongoDB " + label + " 超出 Integer 范围");
        }
        return (int) value;
    }

    private static final class FieldInference {
        private final Set<String> types = new LinkedHashSet<>();
        private int seen;
        private boolean nullSeen;

        private void observe(Object value) {
            seen++;
            nullSeen = nullSeen || value == null;
            if (value != null) {
                types.add(MongoBsonSupport.typeName(value));
            }
        }

        private String typeName() {
            return types.isEmpty() ? "NULL" : types.size() == 1 ? types.iterator().next() : "MIXED";
        }
    }
}
