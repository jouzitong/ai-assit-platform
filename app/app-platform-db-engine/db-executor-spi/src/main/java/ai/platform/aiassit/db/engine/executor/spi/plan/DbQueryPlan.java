package ai.platform.aiassit.db.engine.executor.spi.plan;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbOperationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 已完成语义校验的查询计划。
 *
 * <p>计划与请求 DTO、连接实现解耦，是权限、审计、方言渲染的统一输入。当前保留
 * {@code statement} 承接既有查询语义编译结果；新操作应优先在计划层表达语义，而非直接调用执行器。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbQueryPlan {

    @Builder.Default
    private DbOperationType operationType = DbOperationType.QUERY;

    private String model;

    private String statement;

    @Builder.Default
    private List<Object> parameters = new ArrayList<>();

    private Integer maxRows;
}
