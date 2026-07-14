package ai.platform.aiassit.data.virtualization.spi.query;

import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilter;

import java.util.List;

/** Query semantics to be rendered by an application adapter, not by commons. */
public record PhysicalQuerySpec(
        String table,
        List<PhysicalProjection> projections,
        PhysicalFilter filter,
        boolean countOnly,
        int limit
) {
    public PhysicalQuerySpec {
        projections = projections == null ? List.of() : List.copyOf(projections);
    }
}
