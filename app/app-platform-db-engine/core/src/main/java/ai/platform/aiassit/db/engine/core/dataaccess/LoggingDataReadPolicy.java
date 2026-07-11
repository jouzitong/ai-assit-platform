package ai.platform.aiassit.db.engine.core.dataaccess;

import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.request.DataReadCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 当前数据读取权限策略占位实现。 */
@Slf4j
@Component
public class LoggingDataReadPolicy implements DataReadPolicy {

    @Override
    public void apply(DbAccessContext sourceContext, DataReadCommand command) {
        log.debug("Data read policy placeholder, sourceKey={}, sourceType={}, resource={}",
                sourceContext.getSourceKey(), sourceContext.getSourceType(), command == null ? null : command.getResource());
    }
}
