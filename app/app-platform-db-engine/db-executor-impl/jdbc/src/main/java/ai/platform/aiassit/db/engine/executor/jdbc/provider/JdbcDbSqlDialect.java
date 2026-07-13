package ai.platform.aiassit.db.engine.executor.jdbc.provider;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbOperationType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.BoundSql;
import ai.platform.aiassit.db.engine.executor.spi.plan.DbQueryPlan;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbSqlDialect;
import org.springframework.util.StringUtils;

import java.util.ArrayList;

public class JdbcDbSqlDialect implements DbSqlDialect {

    private final DbAccessDbType dbType;

    public JdbcDbSqlDialect(DbAccessDbType dbType) {
        this.dbType = dbType;
    }

    @Override
    public DbAccessDbType dbType() {
        return dbType;
    }

    @Override
    public BoundSql render(DbQueryPlan plan) throws DbAccessException {
        if (plan == null || plan.getOperationType() != DbOperationType.QUERY) {
            throw new DbAccessException(dbType + " 方言暂只支持查询计划");
        }
        if (!StringUtils.hasText(plan.getStatement())) {
            throw new DbAccessException("查询计划不能为空");
        }
        return BoundSql.builder()
                .sql(plan.getStatement().trim())
                .parameters(plan.getParameters() == null ? new ArrayList<>() : new ArrayList<>(plan.getParameters()))
                .build();
    }
}
