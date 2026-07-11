package ai.platform.aiassit.db.engine.core.dataaccess;

import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.request.DataReadCommand;

/** 协议无关的数据读取权限/数据范围扩展点。 */
public interface DataReadPolicy {

    void apply(DbAccessContext sourceContext, DataReadCommand command);
}
