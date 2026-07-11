package ai.platform.aiassit.db.engine.core.dataaccess;

import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.request.DataReadCommand;
import ai.platform.aiassit.db.engine.executor.spi.result.DataReadResult;

/** 协议无关的数据读取审计扩展点。 */
public interface DataReadAudit {

    void beforeRead(DbAccessContext sourceContext, DataReadCommand command);

    void afterSuccess(DbAccessContext sourceContext, DataReadCommand command, DataReadResult result);

    void afterFailure(DbAccessContext sourceContext, DataReadCommand command, Throwable error);
}
