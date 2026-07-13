package ai.platform.aiassit.db.engine.executor.mongodb.provider;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbOperationType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.BoundSql;
import ai.platform.aiassit.db.engine.executor.spi.plan.DbQueryPlan;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbSqlDialect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;

/** 兼容统一查询管线：statement 实际承载受限的 MongoDB JSON 查询信封。 */
@Component
public class MongoDbSqlDialect implements DbSqlDialect {

    @Override
    public DbAccessDbType dbType() {
        return DbAccessDbType.MONGODB;
    }

    @Override
    public BoundSql render(DbQueryPlan plan) throws DbAccessException {
        if (plan == null || plan.getOperationType() != DbOperationType.QUERY) {
            throw new DbAccessException("MongoDB 方言暂只支持查询计划");
        }
        if (!StringUtils.hasText(plan.getStatement())) {
            throw new DbAccessException("MongoDB 查询计划不能为空");
        }
        return BoundSql.builder()
                .sql(plan.getStatement().trim())
                .parameters(plan.getParameters() == null ? new ArrayList<>() : new ArrayList<>(plan.getParameters()))
                .build();
    }
}
