package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.plan.PhysicalExecutionPlan;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformerRegistry;
import ai.platform.aiassit.data.virtualization.core.transform.TransformOutputMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FieldTransformExecutor {
    private final FieldTransformerRegistry registry;

    public FieldTransformExecutor(FieldTransformerRegistry registry) {
        this.registry = registry;
    }

    public Map<String, Object> readRow(
            CatalogSnapshot snapshot,
            PhysicalExecutionPlan.PhysicalTask task,
            Map<String, Object> physicalRow
    ) {
        Map<String, Object> virtualRow = new LinkedHashMap<>();
        for (CatalogSnapshot.TransformRule rule : task.transformRules()) {
            FieldTransformer transformer = registry.require(rule.readTransformerCode(), rule.readTransformerVersion());
            Map<String, Object> inputs = new LinkedHashMap<>();
            for (CatalogSnapshot.Port port : rule.physicalPorts()) {
                inputs.put(port.code(), value(physicalRow, task.physicalFieldAliases().get(port.physicalFieldMetaId())));
            }
            Map<String, Object> output = TransformOutputMapper.normalizeSnapshot(transformer.read(inputs, rule.readConfig()), rule.virtualPorts());
            for (CatalogSnapshot.Port port : rule.virtualPorts()) {
                CatalogSnapshot.VirtualField field = snapshot.fieldsById().get(port.virtualFieldId());
                if (field != null) virtualRow.put(field.code(), output.get(port.code()));
            }
        }
        return virtualRow;
    }

    private Object value(Map<String, Object> row, String alias) {
        if (row.containsKey(alias)) return row.get(alias);
        return row.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(alias))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }
}
