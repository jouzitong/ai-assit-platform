package ai.platform.aiassit.db.engine.executor.spi.provider;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DataSourceCapabilities;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.request.DataReadCommand;
import ai.platform.aiassit.db.engine.executor.spi.result.DataReadResult;

/**
 * 统一数据源协议适配器。
 *
 * <p>数据库特有的 SQL、表结构维护等能力仍保留在 {@code DbAccessExecutor}，不要求 HTTP、文件等实现。</p>
 */
public interface DataSourceAdapter {

    DbAccessSourceType sourceType();

    DataSourceCapabilities capabilities();

    DataReadResult read(DbAccessContext context, DataReadCommand command) throws DbAccessException;
}
