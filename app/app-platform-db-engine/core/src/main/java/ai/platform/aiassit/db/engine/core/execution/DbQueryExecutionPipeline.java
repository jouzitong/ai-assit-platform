package ai.platform.aiassit.db.engine.core.execution;

import ai.platform.aiassit.db.engine.core.registry.DbSqlDialectRegistry;
import ai.platform.aiassit.db.engine.core.service.DbAccessService;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.BoundSql;
import ai.platform.aiassit.db.engine.executor.spi.plan.DbQueryPlan;
import ai.platform.aiassit.db.engine.executor.spi.request.QueryRequest;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

/** 统一查询主流程：上下文、策略、审计、方言渲染、执行。 */
@Component
public class DbQueryExecutionPipeline {

    private final DbExecutionContextFactory contextFactory;
    private final List<DbExecutionPolicy> policies;
    private final DbOperationAudit audit;
    private final DbSqlDialectRegistry dialectRegistry;
    private final DbAccessService dbAccessService;

    public DbQueryExecutionPipeline(
            DbExecutionContextFactory contextFactory,
            List<DbExecutionPolicy> policies,
            DbOperationAudit audit,
            DbSqlDialectRegistry dialectRegistry,
            DbAccessService dbAccessService
    ) {
        this.contextFactory = contextFactory;
        this.policies = policies;
        this.audit = audit;
        this.dialectRegistry = dialectRegistry;
        this.dbAccessService = dbAccessService;
    }

    public QueryResult execute(DbQueryPlan plan) {
        return execute(null, plan);
    }

    /**
     * 执行已经由上层路由到明确数据源的物理查询计划。
     */
    public QueryResult execute(String sourceKey, DbQueryPlan plan) {
        DbExecutionContext context = sourceKey == null
                ? contextFactory.create(plan.getModel(), plan.getOperationType())
                : contextFactory.create(sourceKey, plan.getModel(), plan.getOperationType());
        try {
            for (DbExecutionPolicy policy : policies) {
                policy.apply(context, plan);
            }
            audit.beforeExecute(context, plan);
            BoundSql boundSql = dialectRegistry.get(context.dbType()).render(plan);
            QueryResult result = dbAccessService.query(context.sourceKey(), QueryRequest.builder()
                    .sql(boundSql.getSql())
                    .parameters(boundSql.getParameters())
                    .maxRows(plan.getMaxRows())
                    .build());
            audit.afterSuccess(context, plan, result);
            return result;
        } catch (DbAccessException ex) {
            audit.afterFailure(context, plan, ex);
            throw new BizException(ex);
        } catch (RuntimeException ex) {
            audit.afterFailure(context, plan, ex);
            throw ex;
        }
    }
}
