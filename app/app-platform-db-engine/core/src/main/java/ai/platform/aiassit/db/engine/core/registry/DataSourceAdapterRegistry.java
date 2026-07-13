package ai.platform.aiassit.db.engine.core.registry;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
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
        DataSourceAdapter matched = null;
        for (DataSourceAdapter adapter : adapters) {
            if (adapter.sourceType() == sourceType) {
                if (matched != null) {
                    throw new DbAccessException("数据源协议存在多个适配器，请使用完整上下文选择: " + sourceType);
                }
                matched = adapter;
            }
        }
        if (matched != null) {
            return matched;
        }
        throw new DbAccessException("未找到数据源适配器: " + sourceType);
    }

    public DataSourceAdapter get(DbAccessContext context) throws DbAccessException {
        DataSourceAdapter matched = null;
        for (DataSourceAdapter adapter : adapters) {
            if (adapter.supports(context)) {
                if (matched != null) {
                    throw new DbAccessException("数据源上下文存在多个适配器: "
                            + context.getSourceType() + "/" + context.getDbType());
                }
                matched = adapter;
            }
        }
        if (matched != null) {
            return matched;
        }
        throw new DbAccessException("未找到数据源适配器: "
                + (context == null ? null : context.getSourceType())
                + "/"
                + (context == null ? null : context.getDbType()));
    }
}
