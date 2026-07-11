package ai.platform.aiassit.db.engine.executor.spi.enums;

/**
 * 数据库引擎统一操作类型。
 *
 * <p>当前主流程只落地 {@link #QUERY}，其余类型用于提前固定执行、授权和审计 SPI 的边界。</p>
 */
public enum DbOperationType {
    QUERY,
    INSERT,
    UPDATE,
    DELETE
}
