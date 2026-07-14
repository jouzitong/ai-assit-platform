package ai.platform.aiassit.db.engine.virtualization.adapter.physical;

import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommand;
import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommandResult;
import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommandSpec;
import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommandType;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilter;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilterOperator;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilterType;
import ai.platform.aiassit.db.engine.core.service.DbAccessService;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.request.ExecuteRequest;
import ai.platform.aiassit.db.engine.executor.spi.result.ExecuteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbEnginePhysicalCommandAdapterTest {

    private DbAccessService dbAccessService;
    private DbEnginePhysicalCommandAdapter adapter;

    @BeforeEach
    void setUp() {
        dbAccessService = mock(DbAccessService.class);
        adapter = new DbEnginePhysicalCommandAdapter(dbAccessService);
        when(dbAccessService.getDbType("primary")).thenReturn(DbAccessDbType.MYSQL);
    }

    @Test
    void executesAParameterizedCommandThroughDbAccessService() {
        when(dbAccessService.execute(eq("primary"), org.mockito.ArgumentMatchers.any(ExecuteRequest.class)))
                .thenReturn(ExecuteResult.builder().affectedRows(1).executionMs(9L).build());
        Map<String, Object> assignments = new LinkedHashMap<>();
        assignments.put("status", "DONE");
        assignments.put("updated_by", 7L);
        PhysicalCommandSpec spec = new PhysicalCommandSpec(
                PhysicalCommandType.UPDATE,
                "orders",
                List.of(),
                assignments,
                predicate("id", PhysicalFilterOperator.EQ, 99L)
        );

        PhysicalCommandResult result = adapter.execute(
                new PhysicalCommand("request-1", "plan-1", "task-1", "primary", spec)
        );

        assertEquals(1, result.affectedRows());
        assertEquals(9L, result.executionMs());
        ArgumentCaptor<ExecuteRequest> requestCaptor = ArgumentCaptor.forClass(ExecuteRequest.class);
        verify(dbAccessService).execute(eq("primary"), requestCaptor.capture());
        assertEquals(
                "UPDATE `orders` SET `status` = ?, `updated_by` = ? WHERE `id` = ?",
                requestCaptor.getValue().getSql()
        );
        assertEquals(List.of("DONE", 7L, 99L), requestCaptor.getValue().getParameters());
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
}
