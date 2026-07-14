package ai.platform.aiassit.db.engine.virtualization.adapter.physical;

import ai.platform.aiassit.data.virtualization.spi.query.PhysicalQueryCommand;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalQueryPort;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalQueryResult;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalQuerySpec;
import ai.platform.aiassit.db.engine.core.execution.DbQueryExecutionPipeline;
import ai.platform.aiassit.db.engine.core.service.DbAccessService;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbOperationType;
import ai.platform.aiassit.db.engine.executor.spi.plan.DbQueryPlan;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Executes a controlled physical query through DB Engine's policy, audit and dialect pipeline. */
@Component
public class DbEnginePhysicalQueryAdapter implements PhysicalQueryPort {

    private final DbAccessService dbAccessService;
    private final DbQueryExecutionPipeline executionPipeline;

    public DbEnginePhysicalQueryAdapter(
            DbAccessService dbAccessService,
            DbQueryExecutionPipeline executionPipeline
    ) {
        this.dbAccessService = dbAccessService;
        this.executionPipeline = executionPipeline;
    }

    @Override
    public PhysicalQueryResult query(PhysicalQueryCommand command) {
        PhysicalQuerySpec spec = requireCommand(command);
        int limit = effectiveLimit(command.maxRows(), spec.limit());
        DbAccessDbType dbType = dbAccessService.getDbType(command.sourceKey());
        ControlledSqlRenderer.RenderedSql rendered = ControlledSqlRenderer.query(spec, limit, dbType);
        DbQueryPlan plan = DbQueryPlan.builder()
                .operationType(DbOperationType.QUERY)
                .model(spec.table())
                .statement(rendered.sql())
                .parameters(rendered.parameters())
                .maxRows(limit)
                .build();
        QueryResult result = executionPipeline.execute(command.sourceKey(), plan);
        List<Map<String, Object>> rows = result == null || result.getRows() == null
                ? List.of()
                : result.getRows();
        boolean truncated = !spec.countOnly() && rows.size() >= limit;
        long executionMs = result == null || result.getExecutionMs() == null ? 0L : result.getExecutionMs();
        long scannedRows = result == null || result.getRowCount() == null ? rows.size() : result.getRowCount();
        return new PhysicalQueryResult(
                rows,
                spec.countOnly() || !truncated,
                truncated,
                scannedRows,
                null,
                executionMs
        );
    }

    private PhysicalQuerySpec requireCommand(PhysicalQueryCommand command) {
        if (command == null || command.querySpec() == null) {
            throw new IllegalArgumentException("physical query command/spec 不能为空");
        }
        if (command.sourceKey() == null || command.sourceKey().isBlank()) {
            throw new IllegalArgumentException("physical query sourceKey 不能为空");
        }
        if (command.timeoutMs() <= 0) {
            throw new IllegalArgumentException("physical query timeoutMs 必须大于 0");
        }
        return command.querySpec();
    }

    private int effectiveLimit(int commandLimit, int specLimit) {
        if (commandLimit <= 0 || specLimit <= 0) {
            throw new IllegalArgumentException("physical query limit/maxRows 必须大于 0");
        }
        return Math.min(commandLimit, specLimit);
    }
}
