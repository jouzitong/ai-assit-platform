package ai.platform.aiassit.data.virtualization.core.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualEntityNamingTest {
    @Test
    void buildsStableCodeFromSourceAndTable() {
        assertEquals("order_db_order_item", VirtualEntityNaming.fromPhysicalTable("order-db", "order_item"));
    }

    @Test
    void prefixesNumericSourceAndLimitsLength() {
        String code = VirtualEntityNaming.fromPhysicalTable("01", "table-name-that-is-deliberately-long-to-exceed-the-virtual-entity-code-limit");

        assertTrue(code.startsWith("v_01_table_name"));
        assertTrue(code.length() <= 64);
    }
}
