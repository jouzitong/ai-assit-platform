package ai.platform.aiassit.db.engine.core.dataaccess;

import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.request.DataReadCommand;
import ai.platform.aiassit.db.engine.executor.spi.result.DataReadResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 当前读取审计占位实现，不打印参数或响应内容。 */
@Slf4j
@Component
public class LoggingDataReadAudit implements DataReadAudit {

    @Override
    public void beforeRead(DbAccessContext sourceContext, DataReadCommand command) {
        log.debug("Data read started, sourceKey={}, sourceType={}, resource={}",
                sourceContext.getSourceKey(), sourceContext.getSourceType(), command == null ? null : command.getResource());
    }

    @Override
    public void afterSuccess(DbAccessContext sourceContext, DataReadCommand command, DataReadResult result) {
        log.debug("Data read succeeded, sourceKey={}, resource={}, recordCount={}",
                sourceContext.getSourceKey(), command == null ? null : command.getResource(), result.getRecords().size());
    }

    @Override
    public void afterFailure(DbAccessContext sourceContext, DataReadCommand command, Throwable error) {
        log.warn("Data read failed, sourceKey={}, resource={}, error={}",
                sourceContext.getSourceKey(), command == null ? null : command.getResource(), error.getMessage());
    }
}
