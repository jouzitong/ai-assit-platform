package ai.platform.aiassit.data.virtualization.core.catalog;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.BindingRole;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RoutingStrategy;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformerRegistry;
import ai.platform.aiassit.data.virtualization.core.transform.TransformDefinition;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformPortEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualFieldEntity;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import ai.platform.aiassit.db.engine.meta.entity.DbTableFieldMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.DbTableMetaEntity;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableFieldMetaMapper;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableMetaMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class CatalogValidator {
    private static final Pattern VIRTUAL_CODE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private final FieldTransformerRegistry transformerRegistry;
    private final DbTableMetaMapper tableMetaMapper;
    private final DbTableFieldMetaMapper fieldMetaMapper;
    private final VirtualCatalogDataRepository repository;

    public CatalogValidator(
            FieldTransformerRegistry transformerRegistry,
            DbTableMetaMapper tableMetaMapper,
            DbTableFieldMetaMapper fieldMetaMapper,
            VirtualCatalogDataRepository repository
    ) {
        this.transformerRegistry = transformerRegistry;
        this.tableMetaMapper = tableMetaMapper;
        this.fieldMetaMapper = fieldMetaMapper;
        this.repository = repository;
    }

    public void validate(CatalogSnapshot snapshot) {
        require(validCode(snapshot.entityCode()), "虚拟实体编码不合法: " + snapshot.entityCode());
        require(!snapshot.fieldsByCode().isEmpty(), "虚拟实体至少需要一个字段");
        require(snapshot.fieldsByCode().size() == snapshot.fieldsByCode().keySet().stream().distinct().count(), "虚拟字段编码重复");
        snapshot.fieldsByCode().values().forEach(field -> {
            require(validCode(field.code()), "虚拟字段编码不合法: " + field.code());
            require(field.logicalType() != null, "虚拟字段缺少 logicalType: " + field.code());
        });

        List<CatalogSnapshot.Binding> activeBindings = snapshot.bindings().stream().filter(CatalogSnapshot.Binding::enabled).toList();
        require(activeBindings.stream().anyMatch(item -> item.readable() && item.role() == BindingRole.PRIMARY), "至少需要一个可读主绑定");
        validateBindingGroups(snapshot, activeBindings);

        for (CatalogSnapshot.Binding binding : activeBindings) {
            validatePhysicalBinding(binding);
            validateRules(snapshot, binding);
        }
        validateRelations(snapshot);
    }

    private void validateBindingGroups(CatalogSnapshot snapshot, List<CatalogSnapshot.Binding> bindings) {
        Map<String, Long> writablePrimaryCount = new HashMap<>();
        Map<String, Long> primaryCount = new HashMap<>();
        for (CatalogSnapshot.Binding binding : bindings) {
            require(validCode(binding.code()), "bindingCode 不合法: " + binding.code());
            require(binding.group() != null && !binding.group().isBlank(), "bindingGroup 不能为空: " + binding.code());
            if (binding.role() == BindingRole.REPLICA) {
                require(!binding.writable(), "副本绑定不能写入: " + binding.code());
            }
            if (binding.role() == BindingRole.PRIMARY && binding.writable()) {
                writablePrimaryCount.merge(binding.group(), 1L, Long::sum);
            }
            if (binding.role() == BindingRole.PRIMARY) {
                primaryCount.merge(binding.group(), 1L, Long::sum);
                validateRouting(snapshot, binding);
            }
        }
        bindings.stream().map(CatalogSnapshot.Binding::group).distinct().forEach(group -> {
            require(primaryCount.getOrDefault(group, 0L) == 1, "绑定组必须且只能有一个主绑定: " + group);
            require(writablePrimaryCount.getOrDefault(group, 0L) <= 1, "绑定组最多只能有一个可写主绑定: " + group);
        });
    }

    private void validateRouting(CatalogSnapshot snapshot, CatalogSnapshot.Binding binding) {
        if (binding.routingConfig() == null || binding.routingConfig().getStrategy() == null
                || binding.routingConfig().getStrategy() == RoutingStrategy.SINGLE) return;
        require(binding.routingConfig().getShardFields() != null && !binding.routingConfig().getShardFields().isEmpty(),
                "分片绑定缺少 shardFields: " + binding.code());
        require(binding.routingConfig().getShardFields().size() == 1,
                "首期路由策略仅支持一个 shardField: " + binding.code());
        binding.routingConfig().getShardFields().forEach(fieldCode -> {
            CatalogSnapshot.VirtualField field = snapshot.fieldsByCode().get(fieldCode);
            require(field != null && field.enabled(), "分片字段不存在或未启用: " + binding.code() + "." + fieldCode);
        });
        if (binding.routingConfig().getStrategy() == RoutingStrategy.HASH) {
            require(binding.routingConfig().getHash() != null && binding.routingConfig().getHash().getModulus() != null
                            && binding.routingConfig().getHash().getModulus() > 0
                            && binding.routingConfig().getHash().getRemainder() != null
                            && binding.routingConfig().getHash().getRemainder() >= 0
                            && binding.routingConfig().getHash().getRemainder() < binding.routingConfig().getHash().getModulus(),
                    "HASH 路由参数不合法: " + binding.code());
        }
        if (binding.routingConfig().getStrategy() == RoutingStrategy.LIST) {
            require(binding.routingConfig().getList() != null && binding.routingConfig().getList().getValues() != null
                            && !binding.routingConfig().getList().getValues().isEmpty(),
                    "LIST 路由参数不合法: " + binding.code());
        }
        if (binding.routingConfig().getStrategy() == RoutingStrategy.RANGE) {
            require(binding.routingConfig().getRange() != null
                            && (binding.routingConfig().getRange().getLower() != null
                            || binding.routingConfig().getRange().getUpper() != null),
                    "RANGE 路由参数不合法: " + binding.code());
        }
    }

    private void validatePhysicalBinding(CatalogSnapshot.Binding binding) {
        DbTableMetaEntity table = tableMetaMapper.selectById(binding.physicalTableMetaId());
        require(table != null && Boolean.TRUE.equals(table.getEnabled()), "绑定引用的物理表不存在或未启用: " + binding.code());
        require(binding.sourceKey().equals(table.getSourceKey()), "绑定 sourceKey 与物理目录不一致: " + binding.code());
        require(binding.physicalTableName().equals(table.getTableName()), "绑定表名快照与物理目录不一致: " + binding.code());
    }

    private void validateRules(CatalogSnapshot snapshot, CatalogSnapshot.Binding binding) {
        List<CatalogSnapshot.TransformRule> rules = snapshot.rules(binding.id()).stream()
                .filter(CatalogSnapshot.TransformRule::enabled).toList();
        Map<Long, Integer> readProducers = new HashMap<>();
        Map<Long, Integer> writeProducers = new HashMap<>();

        for (CatalogSnapshot.TransformRule rule : rules) {
            require(validCode(rule.code()), "字段变换规则编码不合法: " + rule.code());
            require(!rule.physicalPorts().isEmpty() && !rule.virtualPorts().isEmpty(), "字段变换规则必须同时配置物理端口和虚拟端口: " + rule.code());
            require(rule.mode() != null, "字段变换规则缺少 transformMode: " + rule.code());
            if (rule.mode() == ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.TransformMode.READ_ONLY) {
                require(rule.writeTransformerCode() == null || rule.writeTransformerCode().isBlank(),
                        "READ_ONLY 规则不能配置写回变换器: " + rule.code());
            }
            if (rule.mode() == ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.TransformMode.WRITE_ONLY) {
                require(rule.readTransformerCode() == null || rule.readTransformerCode().isBlank(),
                        "WRITE_ONLY 规则不能配置读取变换器: " + rule.code());
            }
            validatePorts(snapshot, binding, rule);
            if (rule.mode().readable()) {
                FieldTransformer transformer = transformerRegistry.require(rule.readTransformerCode(), rule.readTransformerVersion());
                require(transformer.capabilities().readable(), "变换器不支持读取: " + rule.readTransformerCode());
                transformer.validate(definition(rule, rule.readConfig()));
                rule.virtualPorts().forEach(port -> readProducers.merge(port.virtualFieldId(), 1, Integer::sum));
            }
            if (rule.mode().writable()) {
                FieldTransformer transformer = transformerRegistry.require(rule.writeTransformerCode(), rule.writeTransformerVersion());
                require(transformer.capabilities().writable(), "变换器不支持写回: " + rule.writeTransformerCode());
                transformer.validate(definition(rule, rule.writeConfig()));
                rule.physicalPorts().forEach(port -> writeProducers.merge(port.physicalFieldMetaId(), 1, Integer::sum));
            }
        }

        if (binding.readable()) {
            snapshot.fieldsByCode().values().stream().filter(CatalogSnapshot.VirtualField::enabled).forEach(field ->
                    require(readProducers.getOrDefault(field.id(), 0) == 1,
                            "可读绑定中的虚拟字段必须有且只有一个读取生产规则: " + binding.code() + "." + field.code()));
        }
        writeProducers.forEach((fieldId, count) -> require(count == 1, "多个规则竞争写入同一物理字段: " + fieldId));
    }

    private void validatePorts(CatalogSnapshot snapshot, CatalogSnapshot.Binding binding, CatalogSnapshot.TransformRule rule) {
        Set<String> keys = new HashSet<>();
        for (CatalogSnapshot.Port port : rule.physicalPorts()) {
            require(port.side() == FieldSide.PHYSICAL && port.physicalFieldMetaId() != null && port.virtualFieldId() == null,
                    "物理端口字段引用不合法: " + rule.code() + "." + port.code());
            require(validCode(port.code()), "规则端口编码不合法: " + rule.code() + "." + port.code());
            require(keys.add("P:" + port.code()), "规则端口编码重复: " + port.code());
            DbTableFieldMetaEntity physicalField = fieldMetaMapper.selectById(port.physicalFieldMetaId());
            require(physicalField != null && Boolean.TRUE.equals(physicalField.getEnabled()), "物理字段不存在或未启用: " + port.physicalFieldMetaId());
            require(binding.sourceKey().equals(physicalField.getSourceKey())
                            && binding.physicalTableName().equals(physicalField.getTableName()),
                    "物理端口不属于当前绑定: " + rule.code() + "." + port.code());
            require(port.physicalColumnName().equals(physicalField.getColumnName()), "物理字段名快照已漂移: " + port.physicalColumnName());
        }
        for (CatalogSnapshot.Port port : rule.virtualPorts()) {
            require(port.side() == FieldSide.VIRTUAL && port.virtualFieldId() != null && port.physicalFieldMetaId() == null,
                    "虚拟端口字段引用不合法: " + rule.code() + "." + port.code());
            require(validCode(port.code()), "规则端口编码不合法: " + rule.code() + "." + port.code());
            require(keys.add("V:" + port.code()), "规则端口编码重复: " + port.code());
            CatalogSnapshot.VirtualField virtualField = snapshot.fieldsById().get(port.virtualFieldId());
            require(virtualField != null && virtualField.enabled(), "虚拟端口不属于当前实体或字段未启用: " + port.virtualFieldId());
        }
    }

    private void validateRelations(CatalogSnapshot snapshot) {
        Map<String, String> endpointsByCode = new HashMap<>();
        snapshot.relations().stream().filter(CatalogSnapshot.Relation::enabled).forEach(relation -> {
            require(validCode(relation.relationCode()), "虚拟关系编码不合法: " + relation.relationCode());
            String endpoints = relation.sourceEntityId() + ":" + relation.targetEntityId();
            String existingEndpoints = endpointsByCode.putIfAbsent(relation.relationCode(), endpoints);
            require(existingEndpoints == null || endpoints.equals(existingEndpoints),
                    "同一 relationCode 的源目标实体不一致: " + relation.relationCode());
            VirtualFieldEntity source = repository.fieldById(relation.sourceFieldId());
            VirtualFieldEntity target = repository.fieldById(relation.targetFieldId());
            require(source != null && target != null, "虚拟关系引用字段不存在: " + relation.relationCode());
            require(source.getEntityId().equals(relation.sourceEntityId()) && target.getEntityId().equals(relation.targetEntityId()),
                    "虚拟关系字段与实体不匹配: " + relation.relationCode());
            require(source.getLogicalType() == target.getLogicalType(), "虚拟关系字段类型不兼容: " + relation.relationCode());
        });
    }

    private TransformDefinition definition(CatalogSnapshot.TransformRule rule, Map<String, Object> config) {
        return new TransformDefinition(rule.code(), rule.physicalPorts().stream().map(this::entity).toList(),
                rule.virtualPorts().stream().map(this::entity).toList(), config);
    }

    private FieldTransformPortEntity entity(CatalogSnapshot.Port port) {
        FieldTransformPortEntity entity = new FieldTransformPortEntity();
        entity.setId(port.id());
        entity.setFieldSide(port.side());
        entity.setPortCode(port.code());
        entity.setVirtualFieldId(port.virtualFieldId());
        entity.setPhysicalFieldMetaId(port.physicalFieldMetaId());
        entity.setPhysicalColumnName(port.physicalColumnName());
        entity.setOrdinalPosition(port.ordinalPosition());
        entity.setRequiredOnWrite(port.requiredOnWrite());
        return entity;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", message);
        }
    }

    private boolean validCode(String code) {
        return code != null && VIRTUAL_CODE.matcher(code).matches();
    }
}
