package ai.platform.aiassit.data.virtualization.spi.model;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** Parameterized physical predicate tree; {@code field} is a physical column identity, never SQL. */
public record PhysicalFilter(
        PhysicalFilterType type,
        String field,
        PhysicalFilterOperator operator,
        Object value,
        List<Object> values,
        List<PhysicalFilter> children
) {
    public PhysicalFilter {
        values = values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
        children = children == null ? List.of() : List.copyOf(children);
    }
}
