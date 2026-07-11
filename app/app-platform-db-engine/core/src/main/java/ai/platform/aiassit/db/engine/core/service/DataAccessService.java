package ai.platform.aiassit.db.engine.core.service;

import ai.platform.aiassit.db.engine.executor.spi.request.DataReadCommand;
import ai.platform.aiassit.db.engine.executor.spi.result.DataReadResult;

/** 面向任意数据源的统一读取服务。 */
public interface DataAccessService {

    DataReadResult read(String sourceKey, DataReadCommand command);
}
