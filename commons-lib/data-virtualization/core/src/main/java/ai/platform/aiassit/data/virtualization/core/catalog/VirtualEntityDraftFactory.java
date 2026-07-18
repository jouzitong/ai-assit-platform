package ai.platform.aiassit.data.virtualization.core.catalog;

import ai.platform.aiassit.data.virtualization.api.config.BindingRoutingConfig;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.BindingRole;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.TransformMode;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformPortEntity;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformRuleEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualBindingEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualFieldEntity;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import ai.platform.aiassit.data.virtualization.spi.catalog.PhysicalCatalogPort;
import ai.platform.aiassit.data.virtualization.spi.catalog.PhysicalFieldDefinition;
import ai.platform.aiassit.data.virtualization.spi.catalog.PhysicalTableDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class VirtualEntityDraftFactory {
    private final PhysicalCatalogPort physicalCatalogPort;
    private final VirtualCatalogDataRepository repository;
    private final CatalogAssembler assembler;

    public VirtualEntityDraftFactory(
            PhysicalCatalogPort physicalCatalogPort,
            VirtualCatalogDataRepository repository,
            CatalogAssembler assembler
    ) {
        this.physicalCatalogPort = physicalCatalogPort;
        this.repository = repository;
        this.assembler = assembler;
    }

    @Transactional(rollbackFor = Exception.class)
    public CatalogSnapshot create(CreateVirtualEntityFromTableRequest request) {
        if (request == null || request.getPhysicalTableMetaId() == null) {
            throw new VirtualDataException("CATALOG_NOT_FOUND", "物理表 ID 不能为空");
        }
        PhysicalTableDefinition table = physicalCatalogPort.findTable(request.getPhysicalTableMetaId()).orElse(null);
        if (table == null || !table.enabled()) {
            throw new VirtualDataException("CATALOG_NOT_FOUND", "物理表不存在或未启用: " + request.getPhysicalTableMetaId());
        }
        String entityCode = request.getEntityCode() == null || request.getEntityCode().isBlank()
                ? VirtualEntityNaming.fromPhysicalTable(table.sourceKey(), table.tableName())
                : request.getEntityCode().trim();
        if (!entityCode.matches("[A-Za-z][A-Za-z0-9_]{1,63}")) {
            throw new VirtualDataException("CATALOG_VALIDATION_FAILED", "entityCode 只允许字母、数字和下划线，且必须以字母开头");
        }
        if (repository.entityByCode(entityCode) != null) {
            throw new VirtualDataException("CATALOG_VERSION_CONFLICT", "虚拟实体编码已存在: " + entityCode);
        }
        List<PhysicalFieldDefinition> physicalFields = physicalCatalogPort.fields(table.id()).stream()
                .filter(PhysicalFieldDefinition::enabled)
                .sorted(Comparator.comparing(PhysicalFieldDefinition::ordinalPosition,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
        if (physicalFields.isEmpty()) {
            throw new VirtualDataException("FIELD_NOT_FOUND", "物理表没有可用字段: " + table.tableName());
        }

        VirtualEntityEntity entity = new VirtualEntityEntity();
        entity.setEntityCode(entityCode);
        entity.setEntityName(text(request.getEntityName(), entityCode));
        entity.setDescription("由物理表 " + table.sourceKey() + "." + table.tableName() + " 生成");
        entity.setStatus(CatalogStatus.DRAFT);
        entity.setCatalogVersion(0L);
        entity.setEnabled(true);
        repository.insertEntity(entity);

        VirtualBindingEntity binding = new VirtualBindingEntity();
        binding.setEntityId(entity.getId());
        binding.setBindingCode("primary");
        binding.setBindingGroup("default");
        binding.setBindingRole(BindingRole.PRIMARY);
        binding.setPhysicalTableMetaId(table.id());
        binding.setSourceKey(table.sourceKey());
        binding.setPhysicalTableName(table.tableName());
        binding.setReadable(true);
        binding.setWritable(true);
        binding.setReadWeight(100);
        binding.setWritePriority(0);
        binding.setRoutingConfig(new BindingRoutingConfig());
        binding.setEnabled(true);
        repository.insertBinding(binding);

        int ordinal = 0;
        for (PhysicalFieldDefinition physical : physicalFields) {
            VirtualFieldEntity field = new VirtualFieldEntity();
            field.setEntityId(entity.getId());
            field.setFieldCode(toFieldCode(physical.columnName()));
            field.setFieldName(text(physical.columnComment(), physical.columnName()));
            field.setLogicalType(logicalType(physical.dataType()));
            field.setNullable(physical.nullable());
            field.setPrimaryKey(physical.primaryKey());
            field.setOrdinalPosition(ordinal++);
            field.setDefaultValue(physical.defaultValue());
            field.setEnabled(true);
            repository.insertField(field);

            FieldTransformRuleEntity rule = new FieldTransformRuleEntity();
            rule.setBindingId(binding.getId());
            rule.setRuleCode("identity_" + field.getFieldCode());
            rule.setRuleName(field.getFieldName() + "直连规则");
            rule.setTransformMode(TransformMode.BIDIRECTIONAL);
            rule.setReadTransformerCode("identity");
            rule.setReadTransformerVersion(1);
            rule.setWriteTransformerCode("identity");
            rule.setWriteTransformerVersion(1);
            rule.setReadConfig(Map.of("configVersion", 1));
            rule.setWriteConfig(Map.of("configVersion", 1));
            rule.setEnabled(false);
            repository.insertRule(rule);

            FieldTransformPortEntity physicalPort = new FieldTransformPortEntity();
            physicalPort.setRuleId(rule.getId());
            physicalPort.setFieldSide(FieldSide.PHYSICAL);
            physicalPort.setPortCode("physical");
            physicalPort.setPhysicalFieldMetaId(physical.id());
            physicalPort.setPhysicalColumnName(physical.columnName());
            physicalPort.setOrdinalPosition(0);
            physicalPort.setRequiredOnWrite(false);
            repository.insertPort(physicalPort);

            FieldTransformPortEntity virtualPort = new FieldTransformPortEntity();
            virtualPort.setRuleId(rule.getId());
            virtualPort.setFieldSide(FieldSide.VIRTUAL);
            virtualPort.setPortCode("virtual");
            virtualPort.setVirtualFieldId(field.getId());
            virtualPort.setOrdinalPosition(0);
            virtualPort.setRequiredOnWrite(!Boolean.TRUE.equals(field.getNullable()));
            repository.insertPort(virtualPort);
        }
        return assembler.byEntityId(entity.getId());
    }

    private LogicalType logicalType(String sourceType) {
        String type = sourceType == null ? "" : sourceType.toUpperCase(Locale.ROOT);
        if (type.contains("BOOL") || type.equals("BIT")) return LogicalType.BOOLEAN;
        if (type.contains("BIGINT")) return LogicalType.LONG;
        if (type.contains("INT")) return LogicalType.INTEGER;
        if (type.contains("DECIMAL") || type.contains("NUMERIC") || type.contains("DOUBLE") || type.contains("FLOAT")) return LogicalType.DECIMAL;
        if (type.equals("DATE")) return LogicalType.DATE;
        if (type.contains("TIME") || type.contains("DATETIME")) return LogicalType.TIMESTAMP;
        if (type.contains("JSON")) return LogicalType.JSON;
        if (type.contains("BINARY") || type.contains("BLOB")) return LogicalType.BINARY;
        return LogicalType.STRING;
    }

    private String toFieldCode(String column) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char character : column.toCharArray()) {
            if (character == '_' || character == '-' || character == ' ') {
                upper = result.length() > 0;
            } else if (upper) {
                result.append(Character.toUpperCase(character));
                upper = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private String text(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
