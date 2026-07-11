package ai.platform.aiassit.db.engine.core.execution;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbOperationType;
import lombok.Builder;

import java.time.Instant;
import java.util.Set;

/** 单次数据库引擎操作的不可变运行上下文。 */
@Builder
public record DbExecutionContext(
        String requestId,
        Instant startedAt,
        Long userId,
        String username,
        Set<String> roles,
        Set<String> permissions,
        String sourceKey,
        DbAccessDbType dbType,
        String model,
        DbOperationType operationType
) {
}
