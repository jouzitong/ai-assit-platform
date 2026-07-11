package ai.platform.aiassit.db.engine.core.dataaccess;

import ai.platform.aiassit.db.engine.core.registry.DataSourceAdapterRegistry;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.request.DataReadCommand;
import ai.platform.aiassit.db.engine.executor.spi.result.DataReadResult;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

/** 统一数据读取主流程：策略、审计、协议适配器执行。 */
@Component
public class DataReadExecutionPipeline {

    private final List<DataReadPolicy> policies;
    private final DataReadAudit audit;
    private final DataSourceAdapterRegistry adapterRegistry;

    public DataReadExecutionPipeline(
            List<DataReadPolicy> policies,
            DataReadAudit audit,
            DataSourceAdapterRegistry adapterRegistry
    ) {
        this.policies = policies;
        this.audit = audit;
        this.adapterRegistry = adapterRegistry;
    }

    public DataReadResult execute(DbAccessContext sourceContext, DataReadCommand command) {
        try {
            for (DataReadPolicy policy : policies) {
                policy.apply(sourceContext, command);
            }
            audit.beforeRead(sourceContext, command);
            DataReadResult result = adapterRegistry.get(sourceContext.getSourceType()).read(sourceContext, command);
            audit.afterSuccess(sourceContext, command, result);
            return result;
        } catch (DbAccessException ex) {
            audit.afterFailure(sourceContext, command, ex);
            throw new BizException(ex);
        } catch (RuntimeException ex) {
            audit.afterFailure(sourceContext, command, ex);
            throw ex;
        }
    }
}
