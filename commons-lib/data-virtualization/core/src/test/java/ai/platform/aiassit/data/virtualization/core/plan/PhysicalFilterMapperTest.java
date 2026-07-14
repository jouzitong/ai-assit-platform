package ai.platform.aiassit.data.virtualization.core.plan;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilter;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilterOperator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhysicalFilterMapperTest {

    private final PhysicalFilterMapper mapper = new PhysicalFilterMapper();

    @Test
    void shouldMapVirtualFieldToPhysicalColumnWithoutRenderingSql() {
        FilterNode filter = predicate("id", FilterOperator.EQ, 7);

        PhysicalFilter physical = mapper.map(filter, Map.of("id", "order_id"));

        assertEquals("order_id", physical.field());
        assertEquals(PhysicalFilterOperator.EQ, physical.operator());
        assertEquals(7, physical.value());
    }

    @Test
    void shouldRejectFilterWithoutPhysicalIdentityMapping() {
        FilterNode filter = predicate("computedStatus", FilterOperator.EQ, "PAID");

        assertThrows(VirtualDataException.class, () -> mapper.map(filter, Map.of()));
    }

    @Test
    void shouldRejectEmptyInValuesBeforeCallingAdapter() {
        FilterNode filter = predicate("id", FilterOperator.IN, null);

        assertThrows(VirtualDataException.class, () -> mapper.map(filter, Map.of("id", "order_id")));
    }

    private FilterNode predicate(String field, FilterOperator operator, Object value) {
        FilterNode filter = new FilterNode();
        filter.setType(FilterType.PREDICATE);
        filter.setField(field);
        filter.setOperator(operator);
        filter.setValue(value);
        return filter;
    }
}
