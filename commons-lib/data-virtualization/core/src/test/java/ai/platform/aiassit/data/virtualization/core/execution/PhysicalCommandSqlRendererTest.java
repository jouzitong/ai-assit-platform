package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalCommandSqlRendererTest {
    private final PhysicalCommandSqlRenderer renderer = new PhysicalCommandSqlRenderer();

    @Test
    void shouldRenderSafeUpdate() {
        FilterNode filter = new FilterNode();
        filter.setType(FilterType.PREDICATE);
        filter.setField("id");
        filter.setOperator(FilterOperator.EQ);
        filter.setValue(7);

        PhysicalCommandSqlRenderer.CommandSql sql = renderer.update("orders", Map.of("status", "PAID"), filter,
                Map.of("id", "id"), DbAccessDbType.MYSQL);

        assertTrue(sql.sql().contains("UPDATE `orders` SET `status` = ? WHERE `id` = ?"));
        assertEquals(java.util.List.of("PAID", 7), sql.parameters());
    }

    @Test
    void shouldRejectUnboundedUpdate() {
        assertThrows(VirtualDataException.class,
                () -> renderer.update("orders", Map.of("status", "PAID"), null, Map.of(), DbAccessDbType.MYSQL));
    }
}
