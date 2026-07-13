package ai.platform.aiassit.db.engine.executor.jdbc.support;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessDatabase;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** JDBC 产品连接信息以及默认 SQL 兼容模式。 */
public enum JdbcDatabaseProfile {

    POSTGRESQL(DbAccessDbType.POSTGRESQL, 5432, "jdbc:postgresql://", "org.postgresql.Driver", DdlFamily.POSTGRESQL),
    ORACLE(DbAccessDbType.ORACLE, 1521, "jdbc:oracle:thin:@//", "oracle.jdbc.OracleDriver", DdlFamily.ORACLE),
    DM8(DbAccessDbType.DM8, 5236, "jdbc:dm://", "dm.jdbc.driver.DmDriver", DdlFamily.ORACLE),
    KINGBASE_ES(DbAccessDbType.KINGBASE_ES, 54321, "jdbc:kingbase8://", "com.kingbase8.Driver", DdlFamily.POSTGRESQL),
    GAUSSDB(DbAccessDbType.GAUSSDB, 8000, "jdbc:gaussdb://", "com.huawei.gaussdb.jdbc.Driver", DdlFamily.POSTGRESQL),
    OCEANBASE(DbAccessDbType.OCEANBASE, 2881, "jdbc:oceanbase://", "com.oceanbase.jdbc.Driver", DdlFamily.MYSQL),
    TDSQL(DbAccessDbType.TDSQL, 3306, "jdbc:mysql://", "com.mysql.cj.jdbc.Driver", DdlFamily.MYSQL),
    GOLDENDB(DbAccessDbType.GOLDENDB, 3306, "jdbc:mysql://", "com.mysql.cj.jdbc.Driver", DdlFamily.MYSQL),
    GBASE(DbAccessDbType.GBASE, 5258, "jdbc:gbase://", "com.gbase.jdbc.Driver", DdlFamily.MYSQL),
    SHENTONG(DbAccessDbType.SHENTONG, 2003, "jdbc:oscar://", "com.oscar.Driver", DdlFamily.ORACLE);

    private static final Set<DbAccessDbType> SUPPORTED_TYPES = EnumSet.of(
            DbAccessDbType.POSTGRESQL,
            DbAccessDbType.ORACLE,
            DbAccessDbType.DM8,
            DbAccessDbType.KINGBASE_ES,
            DbAccessDbType.GAUSSDB,
            DbAccessDbType.OCEANBASE,
            DbAccessDbType.TDSQL,
            DbAccessDbType.GOLDENDB,
            DbAccessDbType.GBASE,
            DbAccessDbType.SHENTONG
    );

    private final DbAccessDbType dbType;
    private final int defaultPort;
    private final String jdbcPrefix;
    private final String defaultDriverClass;
    private final DdlFamily defaultFamily;

    JdbcDatabaseProfile(
            DbAccessDbType dbType,
            int defaultPort,
            String jdbcPrefix,
            String defaultDriverClass,
            DdlFamily defaultFamily
    ) {
        this.dbType = dbType;
        this.defaultPort = defaultPort;
        this.jdbcPrefix = jdbcPrefix;
        this.defaultDriverClass = defaultDriverClass;
        this.defaultFamily = defaultFamily;
    }

    public static boolean supports(DbAccessDbType dbType) {
        return dbType != null && SUPPORTED_TYPES.contains(dbType);
    }

    public static JdbcDatabaseProfile require(DbAccessDbType dbType) throws DbAccessException {
        for (JdbcDatabaseProfile profile : values()) {
            if (profile.dbType == dbType) {
                return profile;
            }
        }
        throw new DbAccessException("不支持的 JDBC 数据库类型: " + dbType);
    }

    public String resolveJdbcUrl(DbAccessContext context) throws DbAccessException {
        DbAccessDatabase database = context == null ? null : context.getDatabase();
        if (database != null && StringUtils.hasText(database.getJdbcUrl())) {
            return database.getJdbcUrl().trim();
        }
        if (context != null && StringUtils.hasText(context.getEndpoint()) && context.getEndpoint().startsWith("jdbc:")) {
            return context.getEndpoint().trim();
        }
        if (database == null
                || !StringUtils.hasText(database.getHost())
                || (dbType != DbAccessDbType.DM8 && !StringUtils.hasText(database.getDatabaseName()))) {
            throw new DbAccessException("缺少 " + dbType + " 连接配置");
        }
        int port = database.getPort() == null ? defaultPort : database.getPort();
        String baseUrl = jdbcPrefix
                + database.getHost().trim()
                + ":"
                + port;
        if (dbType == DbAccessDbType.DM8) {
            return baseUrl;
        }
        return baseUrl + "/" + database.getDatabaseName().trim();
    }

    public DdlFamily resolveFamily(DbAccessContext context) throws DbAccessException {
        Map<String, Object> attributes = context == null ? null : context.getAttributes();
        Object configured = attributes == null ? null : firstNonNull(attributes.get("compatibilityMode"), attributes.get("ddlDialect"));
        if (configured == null || !StringUtils.hasText(String.valueOf(configured))) {
            return defaultFamily;
        }
        String normalized = String.valueOf(configured).trim().replace('-', '_').toUpperCase(Locale.ROOT);
        if ("PG".equals(normalized) || "POSTGRES".equals(normalized)) {
            normalized = "POSTGRESQL";
        }
        try {
            return DdlFamily.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new DbAccessException("不支持的 compatibilityMode/ddlDialect: " + configured, ex);
        }
    }

    public DbAccessDbType dbType() {
        return dbType;
    }

    public String defaultDriverClass() {
        return defaultDriverClass;
    }

    private static Object firstNonNull(Object first, Object second) {
        return first == null ? second : first;
    }

    public enum DdlFamily {
        MYSQL,
        POSTGRESQL,
        ORACLE,
        ANSI
    }
}
