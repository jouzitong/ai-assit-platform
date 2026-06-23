package ai.platform.aiassit.db.engine.executor.mysql.support;

import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessAuth;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessDatabase;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessNetwork;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

@Component
public class MysqlConnectionSupport {

    public Connection openConnection(DbAccessContext context) throws SQLException, DbAccessException {
        String jdbcUrl = resolveJdbcUrl(context);
        Properties properties = new Properties();
        DbAccessAuth auth = context.getAuth();
        if (auth != null) {
            if (StringUtils.hasText(auth.getUsername())) {
                properties.setProperty("user", auth.getUsername().trim());
            }
            if (auth.getPassword() != null) {
                properties.setProperty("password", auth.getPassword());
            }
        }
        DbAccessNetwork network = context.getNetwork();
        if (network != null) {
            if (network.getConnectTimeoutMs() != null) {
                properties.setProperty("connectTimeout", String.valueOf(network.getConnectTimeoutMs()));
            }
            if (network.getReadTimeoutMs() != null) {
                properties.setProperty("socketTimeout", String.valueOf(network.getReadTimeoutMs()));
            }
        }
        return DriverManager.getConnection(jdbcUrl, properties);
    }

    private String resolveJdbcUrl(DbAccessContext context) throws DbAccessException {
        DbAccessDatabase database = context.getDatabase();
        if (database != null && StringUtils.hasText(database.getJdbcUrl())) {
            return database.getJdbcUrl().trim();
        }
        if (StringUtils.hasText(context.getEndpoint())) {
            return context.getEndpoint().trim();
        }
        if (database == null || !StringUtils.hasText(database.getHost()) || !StringUtils.hasText(database.getDatabaseName())) {
            throw new DbAccessException("缺少 MySQL 连接配置");
        }
        int port = database.getPort() == null ? 3306 : database.getPort();
        return "jdbc:mysql://"
                + database.getHost().trim()
                + ":"
                + port
                + "/"
                + database.getDatabaseName().trim()
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    }
}
