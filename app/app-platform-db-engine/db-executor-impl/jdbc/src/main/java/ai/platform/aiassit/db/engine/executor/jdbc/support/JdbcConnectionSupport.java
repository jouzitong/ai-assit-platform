package ai.platform.aiassit.db.engine.executor.jdbc.support;

import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessAuth;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessNetwork;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

@Component
public class JdbcConnectionSupport {

    private static final String DRIVER_CLASS = "driverClass";
    private static final String COMPATIBILITY_MODE = "compatibilityMode";
    private static final String DDL_DIALECT = "ddlDialect";

    public Connection openConnection(DbAccessContext context) throws SQLException, DbAccessException {
        JdbcDatabaseProfile profile = JdbcDatabaseProfile.require(context == null ? null : context.getDbType());
        Properties properties = connectionProperties(context, profile.resolveFamily(context));
        String jdbcUrl = profile.resolveJdbcUrl(context);
        Driver driver = createDriver(resolveDriverClass(context, profile));
        Connection connection = driver.connect(jdbcUrl, properties);
        if (connection == null) {
            throw new DbAccessException("JDBC 驱动 " + driver.getClass().getName() + " 不接受连接地址: " + jdbcUrl);
        }
        return connection;
    }

    private String resolveDriverClass(DbAccessContext context, JdbcDatabaseProfile profile) {
        Map<String, Object> attributes = context == null ? null : context.getAttributes();
        Object driverClass = attributes == null ? null : attributes.get(DRIVER_CLASS);
        if (driverClass != null && StringUtils.hasText(String.valueOf(driverClass))) {
            return String.valueOf(driverClass).trim();
        }
        return profile.defaultDriverClass();
    }

    private Driver createDriver(String driverClass) throws DbAccessException {
        try {
            Class<?> type = Class.forName(driverClass);
            Object driver = type.getDeclaredConstructor().newInstance();
            if (!(driver instanceof Driver jdbcDriver)) {
                throw new DbAccessException("配置类不是 java.sql.Driver: " + driverClass);
            }
            return jdbcDriver;
        } catch (ReflectiveOperationException ex) {
            throw new DbAccessException("未找到 JDBC 驱动类: " + driverClass + "，请将厂商驱动加入运行时 classpath", ex);
        }
    }

    private Properties connectionProperties(
            DbAccessContext context,
            JdbcDatabaseProfile.DdlFamily family
    ) {
        Properties properties = new Properties();
        addDriverProperties(properties, context == null ? null : context.getAttributes());
        DbAccessAuth auth = context == null ? null : context.getAuth();
        if (auth != null) {
            if (StringUtils.hasText(auth.getUsername())) {
                properties.setProperty("user", auth.getUsername().trim());
            }
            if (auth.getPassword() != null) {
                properties.setProperty("password", auth.getPassword());
            }
        }
        addTimeoutProperties(properties, context == null ? null : context.getNetwork(), family);
        return properties;
    }

    private void addDriverProperties(Properties properties, Map<String, Object> attributes) {
        if (attributes == null) {
            return;
        }
        attributes.forEach((key, value) -> {
            if (StringUtils.hasText(key)
                    && value != null
                    && !isReservedProperty(key)) {
                properties.setProperty(key, String.valueOf(value));
            }
        });
    }

    private boolean isReservedProperty(String key) {
        return DRIVER_CLASS.equalsIgnoreCase(key)
                || COMPATIBILITY_MODE.equalsIgnoreCase(key)
                || DDL_DIALECT.equalsIgnoreCase(key)
                || "user".equalsIgnoreCase(key)
                || "username".equalsIgnoreCase(key)
                || "password".equalsIgnoreCase(key);
    }

    private void addTimeoutProperties(
            Properties properties,
            DbAccessNetwork network,
            JdbcDatabaseProfile.DdlFamily family
    ) {
        if (network == null) {
            return;
        }
        if (family == JdbcDatabaseProfile.DdlFamily.ORACLE) {
            putIfAbsent(properties, "oracle.net.CONNECT_TIMEOUT", network.getConnectTimeoutMs());
            putIfAbsent(properties, "oracle.jdbc.ReadTimeout", network.getReadTimeoutMs());
            return;
        }
        if (family == JdbcDatabaseProfile.DdlFamily.POSTGRESQL) {
            putSecondsIfAbsent(properties, "connectTimeout", network.getConnectTimeoutMs());
            putSecondsIfAbsent(properties, "socketTimeout", network.getReadTimeoutMs());
            return;
        }
        putIfAbsent(properties, "connectTimeout", network.getConnectTimeoutMs());
        putIfAbsent(properties, "socketTimeout", network.getReadTimeoutMs());
    }

    private void putIfAbsent(Properties properties, String key, Integer value) {
        if (value != null && !properties.containsKey(key)) {
            properties.setProperty(key, String.valueOf(value));
        }
    }

    private void putSecondsIfAbsent(Properties properties, String key, Integer milliseconds) {
        if (milliseconds != null && !properties.containsKey(key)) {
            properties.setProperty(key, String.valueOf(Math.max(1, (milliseconds + 999) / 1_000)));
        }
    }
}
