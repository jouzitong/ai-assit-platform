package ai.platform.aiassit.db.engine.core.registry;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbAccessProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DbAccessProviderRegistry {

    private final List<DbAccessProvider> providers;

    public DbAccessProviderRegistry(List<DbAccessProvider> providers) {
        this.providers = providers;
    }

    public DbAccessProvider getProvider(DbAccessSourceType sourceType, DbAccessDbType dbType) throws DbAccessException {
        DbAccessProvider matched = null;
        for (DbAccessProvider provider : providers) {
            if (provider.supports(sourceType, dbType)) {
                if (matched != null) {
                    throw new DbAccessException("存在多个匹配的数据访问执行器: " + sourceType + "/" + dbType);
                }
                matched = provider;
            }
        }
        if (matched != null) {
            return matched;
        }
        throw new DbAccessException("未找到匹配的数据访问执行器: " + sourceType + "/" + dbType);
    }
}
