package ai.platform.aiassit.db.engine.core.execution;

import ai.platform.aiassit.db.engine.core.service.DbAccessService;
import ai.platform.aiassit.db.engine.core.support.DefaultDbSourceKeyResolver;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbOperationType;
import org.arthena.framework.common.context.SystemContext;
import org.athena.framework.security.api.model.AuthorizationSnapshot;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class DbExecutionContextFactory {

    private final DbAccessService dbAccessService;
    private final DefaultDbSourceKeyResolver sourceKeyResolver;

    public DbExecutionContextFactory(DbAccessService dbAccessService, DefaultDbSourceKeyResolver sourceKeyResolver) {
        this.dbAccessService = dbAccessService;
        this.sourceKeyResolver = sourceKeyResolver;
    }

    public DbExecutionContext create(String model, DbOperationType operationType) {
        return create(sourceKeyResolver.resolve(null), model, operationType);
    }

    /**
     * 为已经完成数据源路由的上层计划创建执行上下文。
     */
    public DbExecutionContext create(String sourceKey, String model, DbOperationType operationType) {
        UserContext userContext = SystemContext.getUserContext();
        AuthorizationSnapshot authorization = userContext == null ? null : userContext.authorization();
        return DbExecutionContext.builder()
                .requestId(UUID.randomUUID().toString())
                .startedAt(Instant.now())
                .userId(userContext == null || userContext.subject() == null ? null : userContext.subject().userId())
                .username(userContext == null || userContext.subject() == null ? null : userContext.subject().username())
                .roles(copy(authorization == null ? null : authorization.roles()))
                .permissions(copy(authorization == null ? null : authorization.permissions()))
                .sourceKey(sourceKey)
                .dbType(dbAccessService.getDbType(sourceKey))
                .model(model)
                .operationType(operationType)
                .build();
    }

    private Set<String> copy(Set<String> source) {
        return source == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(source));
    }
}
