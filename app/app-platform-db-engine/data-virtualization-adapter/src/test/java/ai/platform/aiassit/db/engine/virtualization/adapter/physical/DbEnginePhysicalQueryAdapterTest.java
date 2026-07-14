package ai.platform.aiassit.db.engine.virtualization.adapter.physical;

import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilter;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilterOperator;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilterType;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalProjection;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalQueryCommand;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalQueryResult;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalQuerySpec;
import ai.platform.aiassit.db.engine.core.execution.DbQueryExecutionPipeline;
import ai.platform.aiassit.db.engine.core.service.DbAccessService;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.plan.DbQueryPlan;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbEnginePhysicalQueryAdapterTest {

    private DbAccessService dbAccessService;
    private DbQueryExecutionPipeline executionPipeline;
    private DbEnginePhysicalQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        dbAccessService = mock(DbAccessService.class);
        executionPipeline = mock(DbQueryExecutionPipeline.class);
        adapter = new DbEnginePhysicalQueryAdapter(dbAccessService, executionPipeline);
        when(dbAccessService.getDbType("primary")).thenReturn(DbAccessDbType.MYSQL);
    }

    @Test
    void reportsExhaustedWhenProviderReturnsFewerRowsThanEffectiveLimit() {
        List<Map<String, Object>> rows = List.of(
                row("id", 1L),
                row("id", 2L),
                row("id", 3L),
                row("id", 4L)
        );
        when(executionPipeline.execute(eq("primary"), any(DbQueryPlan.class))).thenReturn(
                QueryResult.builder()
                        .rows(rows)
                        .rowCount(4)
                        .executionMs(17L)
                        .build()
        );

        PhysicalQueryResult result = adapter.query(command(false, 10, 5));

        assertEquals(rows, result.rows());
        assertTrue(result.exhausted());
        assertFalse(result.truncated());
        assertEquals(4L, result.scannedRows());
        assertEquals(17L, result.executionMs());

        ArgumentCaptor<DbQueryPlan> planCaptor = ArgumentCaptor.forClass(DbQueryPlan.class);
        verify(executionPipeline).execute(eq("primary"), planCaptor.capture());
        DbQueryPlan plan = planCaptor.getValue();
        assertEquals(5, plan.getMaxRows());
        assertEquals("orders", plan.getModel());
        assertEquals(
                "SELECT `id` AS `id` FROM `orders` WHERE `status` = ? LIMIT 5",
                plan.getStatement()
        );
        assertEquals(List.of("PAID"), plan.getParameters());
    }

    @Test
    void reportsTruncatedConservativelyWhenProviderReturnsTheEffectiveLimit() {
        List<Map<String, Object>> rows = List.of(row("id", 1L), row("id", 2L));
        when(executionPipeline.execute(eq("primary"), any(DbQueryPlan.class))).thenReturn(
                QueryResult.builder()
                        .rows(rows)
                        .rowCount(2)
                        .executionMs(3L)
                        .build()
        );

        PhysicalQueryResult result = adapter.query(command(false, 2, 10));

        assertEquals(rows, result.rows());
        assertFalse(result.exhausted());
        assertTrue(result.truncated());
        assertEquals(2L, result.scannedRows());
    }

    @Test
    void countResultIsAlwaysCompleteAndNeverTruncated() {
        when(executionPipeline.execute(eq("primary"), any(DbQueryPlan.class))).thenReturn(
                QueryResult.builder()
                        .rows(List.of(row("__count", 42L)))
                        .rowCount(1)
                        .executionMs(5L)
                        .build()
        );

        PhysicalQueryResult result = adapter.query(command(true, 1, 1));

        assertTrue(result.exhausted());
        assertFalse(result.truncated());
        assertEquals(List.of(row("__count", 42L)), result.rows());

        ArgumentCaptor<DbQueryPlan> planCaptor = ArgumentCaptor.forClass(DbQueryPlan.class);
        verify(executionPipeline).execute(eq("primary"), planCaptor.capture());
        assertEquals(
                "SELECT COUNT(1) AS `__count` FROM `orders` WHERE `status` = ?",
                planCaptor.getValue().getStatement()
        );
        assertEquals(1, planCaptor.getValue().getMaxRows());
    }

    @Test
    void rejectsInvalidCommandEnvelopeBeforeCallingThePipeline() {
        PhysicalQuerySpec spec = querySpec(false, 10);

        assertThrows(IllegalArgumentException.class,
                () -> adapter.query(new PhysicalQueryCommand("r", "p", "t", "", spec, 10, 1000)));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.query(new PhysicalQueryCommand("r", "p", "t", "primary", spec, 0, 1000)));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.query(new PhysicalQueryCommand("r", "p", "t", "primary", spec, 10, 0)));
    }

    private PhysicalQueryCommand command(boolean countOnly, int maxRows, int specLimit) {
        return new PhysicalQueryCommand(
                "request-1",
                "plan-1",
                "task-1",
                "primary",
                querySpec(countOnly, specLimit),
                maxRows,
                1_000
        );
    }

    private PhysicalQuerySpec querySpec(boolean countOnly, int limit) {
        return new PhysicalQuerySpec(
                "orders",
                countOnly ? List.of() : List.of(new PhysicalProjection("id", "id")),
                predicate("status", PhysicalFilterOperator.EQ, "PAID"),
                countOnly,
                limit
        );
    }

    private PhysicalFilter predicate(String field, PhysicalFilterOperator operator, Object value) {
        return new PhysicalFilter(
                PhysicalFilterType.PREDICATE,
                field,
                operator,
                value,
                List.of(),
                List.of()
        );
    }

    private Map<String, Object> row(Object... pairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            row.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return row;
    }
}
