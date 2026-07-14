package ai.platform.aiassit.data.virtualization.core.catalog;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.BindingRole;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.TransformMode;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformPortEntity;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformRuleEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualBindingEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualFieldEntity;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CatalogAssembler {
    private final VirtualCatalogDataRepository repository;

    public CatalogAssembler(VirtualCatalogDataRepository repository) {
        this.repository = repository;
    }

    public CatalogSnapshot byEntityId(Long entityId) {
        VirtualEntityEntity entity = repository.entityById(entityId);
        if (entity == null) {
            throw new VirtualDataException("CATALOG_NOT_FOUND", "虚拟实体不存在: " + entityId);
        }
        return assemble(entity);
    }

    public CatalogSnapshot byEntityCode(String entityCode) {
        VirtualEntityEntity entity = repository.entityByCode(entityCode);
        if (entity == null) {
            throw new VirtualDataException("CATALOG_NOT_FOUND", "虚拟实体不存在: " + entityCode);
        }
        return assemble(entity);
    }

    private CatalogSnapshot assemble(VirtualEntityEntity entity) {
        List<VirtualFieldEntity> fields = repository.fields(entity.getId());
        List<VirtualBindingEntity> bindings = repository.bindings(entity.getId());
        List<FieldTransformRuleEntity> rules = repository.rules(bindings.stream().map(VirtualBindingEntity::getId).toList());
        List<FieldTransformPortEntity> ports = repository.ports(rules.stream().map(FieldTransformRuleEntity::getId).toList());

        Map<Long, List<FieldTransformPortEntity>> portsByRule = new LinkedHashMap<>();
        ports.forEach(port -> portsByRule.computeIfAbsent(port.getRuleId(), key -> new ArrayList<>()).add(port));

        Map<String, CatalogSnapshot.VirtualField> fieldsByCode = new LinkedHashMap<>();
        Map<Long, CatalogSnapshot.VirtualField> fieldsById = new LinkedHashMap<>();
        for (VirtualFieldEntity field : fields) {
            CatalogSnapshot.VirtualField item = new CatalogSnapshot.VirtualField(
                    field.getId(), field.getFieldCode(), field.getFieldName(), field.getLogicalType(),
                    Boolean.TRUE.equals(field.getNullable()), Boolean.TRUE.equals(field.getPrimaryKey()),
                    value(field.getOrdinalPosition()), Boolean.TRUE.equals(field.getEnabled())
            );
            fieldsByCode.put(item.code(), item);
            fieldsById.put(item.id(), item);
        }

        List<CatalogSnapshot.Binding> bindingItems = bindings.stream().map(binding -> new CatalogSnapshot.Binding(
                binding.getId(), binding.getBindingCode(), binding.getBindingGroup(),
                binding.getBindingRole() == null ? BindingRole.PRIMARY : binding.getBindingRole(),
                binding.getPhysicalTableMetaId(), binding.getSourceKey(), binding.getPhysicalTableName(),
                Boolean.TRUE.equals(binding.getReadable()), Boolean.TRUE.equals(binding.getWritable()),
                value(binding.getReadWeight()), value(binding.getWritePriority()), binding.getRoutingConfig(),
                Boolean.TRUE.equals(binding.getEnabled())
        )).toList();

        Map<Long, List<CatalogSnapshot.TransformRule>> rulesByBinding = new LinkedHashMap<>();
        for (FieldTransformRuleEntity rule : rules) {
            List<CatalogSnapshot.Port> physical = new ArrayList<>();
            List<CatalogSnapshot.Port> virtual = new ArrayList<>();
            portsByRule.getOrDefault(rule.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(port -> value(port.getOrdinalPosition())))
                    .map(this::port)
                    .forEach(port -> (port.side() == FieldSide.PHYSICAL ? physical : virtual).add(port));
            CatalogSnapshot.TransformRule item = new CatalogSnapshot.TransformRule(
                    rule.getId(), rule.getBindingId(), rule.getRuleCode(), rule.getRuleName(),
                    rule.getTransformMode(),
                    rule.getReadTransformerCode(), version(rule.getReadTransformerVersion()),
                    rule.getWriteTransformerCode(), version(rule.getWriteTransformerVersion()),
                    rule.getReadConfig(), rule.getWriteConfig(), Boolean.TRUE.equals(rule.getEnabled()), physical, virtual
            );
            rulesByBinding.computeIfAbsent(rule.getBindingId(), key -> new ArrayList<>()).add(item);
        }

        List<CatalogSnapshot.Relation> relationItems = repository.relations(entity.getId()).stream()
                .map(relation -> new CatalogSnapshot.Relation(
                        relation.getId(), relation.getRelationCode(), relation.getRelationName(),
                        relation.getSourceEntityId(), relation.getSourceFieldId(), relation.getTargetEntityId(),
                        relation.getTargetFieldId(), relation.getResultMode(), Boolean.TRUE.equals(relation.getEnabled())
                )).toList();

        return new CatalogSnapshot(
                entity.getId(), entity.getEntityCode(), entity.getEntityName(),
                entity.getStatus() == null ? CatalogStatus.DRAFT : entity.getStatus(),
                entity.getCatalogVersion() == null ? 0 : entity.getCatalogVersion(), Boolean.TRUE.equals(entity.getEnabled()),
                fieldsByCode, fieldsById, bindingItems, rulesByBinding, relationItems
        );
    }

    private CatalogSnapshot.Port port(FieldTransformPortEntity port) {
        return new CatalogSnapshot.Port(
                port.getId(), port.getFieldSide(), port.getPortCode(), port.getVirtualFieldId(),
                port.getPhysicalFieldMetaId(), port.getPhysicalColumnName(), value(port.getOrdinalPosition()),
                Boolean.TRUE.equals(port.getRequiredOnWrite())
        );
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private int version(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }
}
