package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterEvaluatorTest {
    @Test
    void shouldEvaluateNestedStrongTypedFilter() {
        FilterNode amount = predicate("amount", FilterOperator.GTE, 10);
        FilterNode status = predicate("status", FilterOperator.IN, null);
        status.setValues(List.of("PAID", "DONE"));
        FilterNode and = new FilterNode();
        and.setType(FilterType.AND);
        and.setChildren(List.of(amount, status));

        FilterEvaluator evaluator = new FilterEvaluator();
        assertTrue(evaluator.test(and, Map.of("amount", 12.5, "status", "PAID")));
        assertFalse(evaluator.test(and, Map.of("amount", 9, "status", "PAID")));
    }

    private FilterNode predicate(String field, FilterOperator operator, Object value) {
        FilterNode node = new FilterNode();
        node.setType(FilterType.PREDICATE);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(value);
        return node;
    }
}
