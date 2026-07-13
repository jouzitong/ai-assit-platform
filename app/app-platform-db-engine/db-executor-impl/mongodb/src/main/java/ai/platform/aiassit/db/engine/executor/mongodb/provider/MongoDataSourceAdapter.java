package ai.platform.aiassit.db.engine.executor.mongodb.provider;

import ai.platform.aiassit.db.engine.executor.mongodb.support.MongoBsonSupport;
import ai.platform.aiassit.db.engine.executor.mongodb.support.MongoCommandEnvelope;
import ai.platform.aiassit.db.engine.executor.mongodb.support.MongoConnectionSupport;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DataSourceCapabilities;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.provider.DataSourceAdapter;
import ai.platform.aiassit.db.engine.executor.spi.request.DataReadCommand;
import ai.platform.aiassit.db.engine.executor.spi.result.DataReadResult;
import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MongoDataSourceAdapter implements DataSourceAdapter {

    private final MongoConnectionSupport connectionSupport;

    public MongoDataSourceAdapter(MongoConnectionSupport connectionSupport) {
        this.connectionSupport = connectionSupport;
    }

    @Override
    public DbAccessSourceType sourceType() {
        return DbAccessSourceType.DATABASE;
    }

    @Override
    public boolean supports(DbAccessContext context) {
        return context != null
                && context.getSourceType() == DbAccessSourceType.DATABASE
                && context.getDbType() == DbAccessDbType.MONGODB;
    }

    @Override
    public DataSourceCapabilities capabilities() {
        return DataSourceCapabilities.readOnly();
    }

    @Override
    public DataReadResult read(DbAccessContext context, DataReadCommand command) throws DbAccessException {
        if (command == null || !StringUtils.hasText(command.getResource())) {
            throw new DbAccessException("MongoDB 读取 resource 不能为空");
        }
        String collectionName = MongoCommandEnvelope.requireCollection(command.getResource());
        int page = command.getPage() == null || command.getPage() < 1 ? 1 : command.getPage();
        int pageSize = command.getPageSize() == null || command.getPageSize() < 1
                ? 100 : Math.min(command.getPageSize(), 1_000);
        long offsetValue = (long) (page - 1) * pageSize;
        if (offsetValue > Integer.MAX_VALUE) {
            throw new DbAccessException("MongoDB 分页偏移量过大");
        }
        Document filter = new Document();
        if (command.getParameters() != null) {
            for (Map.Entry<String, Object> entry : new LinkedHashMap<>(command.getParameters()).entrySet()) {
                if (!StringUtils.hasText(entry.getKey()) || entry.getKey().startsWith("$")) {
                    throw new DbAccessException("MongoDB 过滤字段不合法: " + entry.getKey());
                }
                filter.put(entry.getKey(), entry.getValue());
            }
        }
        Instant started = Instant.now();
        try {
            MongoDatabase database = connectionSupport.database(context, null);
            if (database.listCollections().filter(new Document("name", collectionName)).first() == null) {
                throw new DbAccessException("MongoDB collection 不存在: " + collectionName);
            }
            MongoCollection<Document> collection = database.getCollection(collectionName);
            FindIterable<Document> iterable = collection.find(filter).skip((int) offsetValue).limit(pageSize);
            List<Map<String, Object>> records = new ArrayList<>();
            for (Document document : iterable) {
                records.add(MongoBsonSupport.toSafeDocument(document));
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("protocol", "MONGODB");
            metadata.put("executionMs", Duration.between(started, Instant.now()).toMillis());
            return DataReadResult.builder().records(records).metadata(metadata).build();
        } catch (MongoException ex) {
            throw new DbAccessException("读取 MongoDB collection 失败", ex);
        }
    }
}
