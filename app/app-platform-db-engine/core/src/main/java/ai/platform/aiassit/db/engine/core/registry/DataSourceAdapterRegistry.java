package ai.platform.aiassit.db.engine.core.registry;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.provider.DataSourceAdapter;
import org.springframework.stereotype.Component;

import java.util.List;

/** 按数据源协议选择读取适配器。 */
@Component
public class DataSourceAdapterRegistry {

    private final List<DataSourceAdapter> adapters;

    public DataSourceAdapterRegistry(List<DataSourceAdapter> adapters) {
        this.adapters = adapters;
    }

    public DataSourceAdapter get(DbAccessSourceType sourceType) throws DbAccessException {
        for (DataSourceAdapter adapter : adapters) {
            if (adapter.sourceType() == sourceType) {
                return adapter;
            }
        }
        throw new DbAccessException("未找到数据源适配器: " + sourceType);
    }
}
