package ai.platform.aiassit.data.virtualization.core.catalog;

import ai.platform.aiassit.data.virtualization.api.config.BindingRoutingConfig;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.BindingRole;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RelationResultMode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.TransformMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 单次执行持有的不可变目录视图。 */
public record CatalogSnapshot(
        Long entityId,
        String entityCode,
        String entityName,
        CatalogStatus status,
        long catalogVersion,
        boolean enabled,
        Map<String, VirtualField> fieldsByCode,
        Map<Long, VirtualField> fieldsById,
        List<Binding> bindings,
        Map<Long, List<TransformRule>> rulesByBinding,
        List<Relation> relations
) {
    public CatalogSnapshot {
        fieldsByCode = Map.copyOf(new LinkedHashMap<>(fieldsByCode));
        fieldsById = Map.copyOf(new LinkedHashMap<>(fieldsById));
        bindings = List.copyOf(bindings);
        Map<Long, List<TransformRule>> copy = new LinkedHashMap<>();
        rulesByBinding.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        rulesByBinding = Map.copyOf(copy);
        relations = List.copyOf(relations);
    }

    public List<TransformRule> rules(Long bindingId) {
        return rulesByBinding.getOrDefault(bindingId, List.of());
    }

    public TransformRule readableRule(Long bindingId, Long virtualFieldId) {
        return rules(bindingId).stream()
                .filter(TransformRule::enabled)
                .filter(rule -> rule.mode().readable())
                .filter(rule -> rule.virtualPorts().stream().anyMatch(port -> virtualFieldId.equals(port.virtualFieldId())))
                .findFirst().orElse(null);
    }

    public List<TransformRule> writableRules(Long bindingId) {
        return rules(bindingId).stream().filter(TransformRule::enabled).filter(rule -> rule.mode().writable()).toList();
    }

    public List<Relation> relationGroup(String relationCode) {
        return relations.stream()
                .filter(Relation::enabled)
                .filter(item -> entityId.equals(item.sourceEntityId()))
                .filter(item -> item.relationCode().equals(relationCode))
                .toList();
    }

    public record VirtualField(
            Long id, String code, String name, LogicalType logicalType, boolean nullable,
            boolean primaryKey, int ordinalPosition, boolean enabled
    ) {
    }

    public record Binding(
            Long id, String code, String group, BindingRole role, Long physicalTableMetaId,
            String sourceKey, String physicalTableName, boolean readable, boolean writable,
            int readWeight, int writePriority, BindingRoutingConfig routingConfig, boolean enabled
    ) {
    }

    public record TransformRule(
            Long id, Long bindingId, String code, String name, TransformMode mode,
            String readTransformerCode, int readTransformerVersion,
            String writeTransformerCode, int writeTransformerVersion,
            Map<String, Object> readConfig, Map<String, Object> writeConfig,
            boolean enabled, List<Port> physicalPorts, List<Port> virtualPorts
    ) {
        public TransformRule {
            readConfig = readConfig == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(readConfig));
            writeConfig = writeConfig == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(writeConfig));
            physicalPorts = List.copyOf(new ArrayList<>(physicalPorts));
            virtualPorts = List.copyOf(new ArrayList<>(virtualPorts));
        }
    }

    public record Port(
            Long id, FieldSide side, String code, Long virtualFieldId, Long physicalFieldMetaId,
            String physicalColumnName, int ordinalPosition, boolean requiredOnWrite
    ) {
    }

    public record Relation(
            Long id, String relationCode, String relationName, Long sourceEntityId, Long sourceFieldId,
            Long targetEntityId, Long targetFieldId, RelationResultMode resultMode, boolean enabled
    ) {
        public Relation {
            resultMode = resultMode == null ? RelationResultMode.OBJECT : resultMode;
        }

        public Relation(
                Long id, String relationCode, String relationName, Long sourceEntityId, Long sourceFieldId,
                Long targetEntityId, Long targetFieldId, boolean enabled
        ) {
            this(id, relationCode, relationName, sourceEntityId, sourceFieldId,
                    targetEntityId, targetFieldId, RelationResultMode.OBJECT, enabled);
        }
    }
}
