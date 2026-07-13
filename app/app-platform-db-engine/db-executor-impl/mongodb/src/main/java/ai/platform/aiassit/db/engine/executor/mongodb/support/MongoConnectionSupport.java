package ai.platform.aiassit.db.engine.executor.mongodb.support;

import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessAuth;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessDatabase;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessNetwork;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** MongoDB 客户端创建与连接池生命周期管理。 */
@Component
public class MongoConnectionSupport implements DisposableBean {

    private static final int DEFAULT_PORT = 27017;
    private static final String DEFAULT_APPLICATION_NAME = "ai-assit-platform-db-engine";

    private final Map<String, ClientHolder> clients = new LinkedHashMap<>();

    public MongoDatabase database(DbAccessContext context, String requestedDatabase) throws DbAccessException {
        ClientConfig config = resolveConfig(context);
        String databaseName = resolveDatabaseName(context, config.connectionString(), requestedDatabase);
        return client(context, config).getDatabase(databaseName);
    }

    public String databaseName(DbAccessContext context, String requestedDatabase) throws DbAccessException {
        ClientConfig config = resolveConfig(context);
        return resolveDatabaseName(context, config.connectionString(), requestedDatabase);
    }

    private synchronized MongoClient client(DbAccessContext context, ClientConfig config) throws DbAccessException {
        String cacheKey = StringUtils.hasText(context.getSourceKey())
                ? context.getSourceKey().trim()
                : config.fingerprint();
        ClientHolder holder = clients.get(cacheKey);
        if (holder != null && holder.fingerprint().equals(config.fingerprint())) {
            return holder.client();
        }
        MongoClient replacement;
        try {
            replacement = MongoClients.create(buildSettings(context, config.connectionString()));
        } catch (RuntimeException ex) {
            throw new DbAccessException("创建 MongoDB 客户端失败", ex);
        }
        if (holder != null) {
            holder.client().close();
        }
        clients.put(cacheKey, new ClientHolder(config.fingerprint(), replacement));
        return replacement;
    }

    private MongoClientSettings buildSettings(DbAccessContext context, ConnectionString connectionString) throws DbAccessException {
        MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applicationName(attributeString(context, "applicationName", DEFAULT_APPLICATION_NAME));

        DbAccessAuth auth = context.getAuth();
        if (auth != null && StringUtils.hasText(auth.getUsername())) {
            if (connectionString.getCredential() != null) {
                throw new DbAccessException("MongoDB URI 与 credential 不能同时配置认证信息");
            }
            String authSource = attributeString(context, "authSource", null);
            if (!StringUtils.hasText(authSource)) {
                authSource = configuredDatabaseName(context, connectionString);
            }
            if (!StringUtils.hasText(authSource)) {
                authSource = "admin";
            }
            char[] password = auth.getPassword() == null ? new char[0] : auth.getPassword().toCharArray();
            MongoCredential credential = MongoCredential.createCredential(
                    auth.getUsername().trim(), authSource.trim(), password);
            builder.credential(credential);
        }

        DbAccessNetwork network = context.getNetwork();
        if (network != null) {
            Integer connectTimeout = positive(network.getConnectTimeoutMs());
            Integer readTimeout = positive(network.getReadTimeoutMs());
            if (connectTimeout != null || readTimeout != null) {
                Integer finalConnectTimeout = connectTimeout;
                Integer finalReadTimeout = readTimeout;
                builder.applyToSocketSettings(settings -> {
                    if (finalConnectTimeout != null) {
                        settings.connectTimeout(finalConnectTimeout, TimeUnit.MILLISECONDS);
                    }
                    if (finalReadTimeout != null) {
                        settings.readTimeout(finalReadTimeout, TimeUnit.MILLISECONDS);
                    }
                });
            }
            if (connectTimeout != null) {
                builder.applyToClusterSettings(settings ->
                        settings.serverSelectionTimeout(connectTimeout, TimeUnit.MILLISECONDS));
            }
        }

        Integer maxPoolSize = attributeInteger(context, "maxPoolSize");
        Integer minPoolSize = attributeInteger(context, "minPoolSize");
        if (maxPoolSize != null || minPoolSize != null) {
            builder.applyToConnectionPoolSettings(settings -> {
                if (maxPoolSize != null && maxPoolSize > 0) {
                    settings.maxSize(maxPoolSize);
                }
                if (minPoolSize != null && minPoolSize >= 0) {
                    settings.minSize(minPoolSize);
                }
            });
        }
        return builder.build();
    }

    private ClientConfig resolveConfig(DbAccessContext context) throws DbAccessException {
        if (context == null) {
            throw new DbAccessException("MongoDB 数据源上下文不能为空");
        }
        DbAccessDatabase database = context.getDatabase();
        if (database == null) {
            throw new DbAccessException("缺少 MongoDB 连接配置");
        }
        String uri;
        if (StringUtils.hasText(database.getJdbcUrl())) {
            uri = database.getJdbcUrl().trim();
            if (!uri.startsWith("mongodb://") && !uri.startsWith("mongodb+srv://")) {
                throw new DbAccessException("MongoDB 连接地址必须以 mongodb:// 或 mongodb+srv:// 开头");
            }
        } else {
            if (!StringUtils.hasText(database.getHost())) {
                throw new DbAccessException("MongoDB host 不能为空");
            }
            int port = database.getPort() == null ? DEFAULT_PORT : database.getPort();
            String databaseName = firstText(database.getDatabaseName(), database.getSchemaName());
            uri = "mongodb://" + database.getHost().trim() + ":" + port
                    + (StringUtils.hasText(databaseName) ? "/" + databaseName.trim() : "");
        }
        ConnectionString connectionString;
        try {
            connectionString = new ConnectionString(uri);
        } catch (IllegalArgumentException ex) {
            throw new DbAccessException("MongoDB 连接地址不合法", ex);
        }
        WriteConcern writeConcern = connectionString.getWriteConcern();
        if (writeConcern != null && !writeConcern.isAcknowledged()) {
            throw new DbAccessException("MongoDB 不允许使用未确认写入配置（w=0）");
        }
        return new ClientConfig(connectionString, fingerprint(context, uri));
    }

    private String resolveDatabaseName(
            DbAccessContext context,
            ConnectionString connectionString,
            String requestedDatabase
    ) throws DbAccessException {
        String configured = configuredDatabaseName(context, connectionString);
        if (StringUtils.hasText(requestedDatabase)) {
            String requested = requestedDatabase.trim();
            boolean allowCrossDatabase = Boolean.TRUE.equals(attributeBoolean(context, "allowCrossDatabase"));
            if (StringUtils.hasText(configured) && !configured.equals(requested) && !allowCrossDatabase) {
                throw new DbAccessException("不允许访问配置之外的 MongoDB database: " + requested);
            }
            return requested;
        }
        if (!StringUtils.hasText(configured)) {
            throw new DbAccessException("缺少 MongoDB database 配置");
        }
        return configured;
    }

    private String configuredDatabaseName(DbAccessContext context, ConnectionString connectionString) {
        DbAccessDatabase database = context.getDatabase();
        if (database != null) {
            String configured = firstText(database.getDatabaseName(), database.getSchemaName());
            if (StringUtils.hasText(configured)) {
                return configured.trim();
            }
        }
        return connectionString.getDatabase();
    }

    private String fingerprint(DbAccessContext context, String uri) throws DbAccessException {
        DbAccessAuth auth = context.getAuth();
        DbAccessNetwork network = context.getNetwork();
        DbAccessDatabase database = context.getDatabase();
        String material = uri + "\u0000"
                + (auth == null ? "" : String.valueOf(auth.getUsername())) + "\u0000"
                + (auth == null ? "" : String.valueOf(auth.getPassword())) + "\u0000"
                + (database == null ? "" : String.valueOf(database.getDatabaseName())) + "\u0000"
                + (database == null ? "" : String.valueOf(database.getSchemaName())) + "\u0000"
                + (network == null ? "" : network.toString()) + "\u0000"
                + String.valueOf(context.getAttributes());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new DbAccessException("无法计算 MongoDB 连接配置指纹", ex);
        }
    }

    private String attributeString(DbAccessContext context, String name, String defaultValue) {
        Object value = attributes(context).get(name);
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? defaultValue : String.valueOf(value).trim();
    }

    private Integer attributeInteger(DbAccessContext context, String name) throws DbAccessException {
        Object value = attributes(context).get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new DbAccessException("MongoDB 属性 " + name + " 必须是整数", ex);
        }
    }

    private Boolean attributeBoolean(DbAccessContext context, String name) {
        Object value = attributes(context).get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    private Map<String, Object> attributes(DbAccessContext context) {
        return context.getAttributes() == null ? Map.of() : context.getAttributes();
    }

    private Integer positive(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    @Override
    public synchronized void destroy() {
        clients.values().forEach(holder -> holder.client().close());
        clients.clear();
    }

    private record ClientConfig(ConnectionString connectionString, String fingerprint) {
    }

    private record ClientHolder(String fingerprint, MongoClient client) {
    }
}
