package ai.platform.aiassit.db.engine.virtualization.adapter.physical;

import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommand;
import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommandPort;
import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommandResult;
import ai.platform.aiassit.db.engine.core.service.DbAccessService;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.request.ExecuteRequest;
import ai.platform.aiassit.db.engine.executor.spi.result.ExecuteResult;
import org.springframework.stereotype.Component;

/** Executes parameterized physical mutations through the DB Engine provider boundary. */
@Component
public class DbEnginePhysicalCommandAdapter implements PhysicalCommandPort {

    private final DbAccessService dbAccessService;

    public DbEnginePhysicalCommandAdapter(DbAccessService dbAccessService) {
        this.dbAccessService = dbAccessService;
    }

    @Override
    public PhysicalCommandResult execute(PhysicalCommand command) {
        if (command == null || command.commandSpec() == null) {
            throw new IllegalArgumentException("physical command/spec 不能为空");
        }
        if (command.sourceKey() == null || command.sourceKey().isBlank()) {
            throw new IllegalArgumentException("physical command sourceKey 不能为空");
        }
        DbAccessDbType dbType = dbAccessService.getDbType(command.sourceKey());
        ControlledSqlRenderer.RenderedSql rendered = ControlledSqlRenderer.command(command.commandSpec(), dbType);
        ExecuteResult result = dbAccessService.execute(command.sourceKey(), ExecuteRequest.builder()
                .sql(rendered.sql())
                .parameters(rendered.parameters())
                .build());
        int affectedRows = result == null || result.getAffectedRows() == null ? 0 : result.getAffectedRows();
        long executionMs = result == null || result.getExecutionMs() == null ? 0L : result.getExecutionMs();
        return new PhysicalCommandResult(affectedRows, executionMs);
    }
}
