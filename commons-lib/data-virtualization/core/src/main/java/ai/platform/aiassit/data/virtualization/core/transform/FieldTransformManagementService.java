package ai.platform.aiassit.data.virtualization.core.transform;

import ai.platform.aiassit.data.virtualization.api.dto.TransformLineageResponse;
import ai.platform.aiassit.data.virtualization.api.dto.TransformPreviewRequest;
import ai.platform.aiassit.data.virtualization.api.dto.TransformPreviewResponse;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformPortEntity;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformRuleEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualFieldEntity;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FieldTransformManagementService {
    private final VirtualCatalogDataRepository repository;
    private final FieldTransformerRegistry registry;

    public FieldTransformManagementService(VirtualCatalogDataRepository repository, FieldTransformerRegistry registry) {
        this.repository = repository;
        this.registry = registry;
    }

    public List<FieldTransformerRegistry.Descriptor> transformers() {
        return registry.descriptors();
    }

    public void validate(Long ruleId) {
        RuleContext context = context(ruleId);
        if (context.rule().getTransformMode() == null) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "字段变换规则缺少 transformMode: " + ruleId);
        }
        if (context.rule().getTransformMode().readable()) {
            registry.require(context.rule().getReadTransformerCode(), context.rule().getReadTransformerVersion())
                    .validate(definition(context, scriptConfig(context, context.rule().getReadConfig(), context.rule().getScriptCode(), "read")));
        }
        if (context.rule().getTransformMode().writable()) {
            registry.require(context.rule().getWriteTransformerCode(), context.rule().getWriteTransformerVersion())
                    .validate(definition(context, scriptConfig(context, context.rule().getWriteConfig(), context.rule().getScriptCode(), "write")));
        }
    }

    public TransformPreviewResponse preview(TransformPreviewRequest request) {
        if (request == null || request.getRuleId() == null) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "ruleId 不能为空");
        }
        RuleContext context = context(request.getRuleId());
        boolean write = Boolean.TRUE.equals(request.getWriteDirection());
        if (context.rule().getTransformMode() == null
                || write && !context.rule().getTransformMode().writable()
                || !write && !context.rule().getTransformMode().readable()) {
            throw new VirtualDataException("FIELD_TRANSFORM_WRITE_UNSUPPORTED",
                    "规则不支持请求的预览方向: " + context.rule().getRuleCode());
        }
        String code = write ? context.rule().getWriteTransformerCode() : context.rule().getReadTransformerCode();
        Integer version = write ? context.rule().getWriteTransformerVersion() : context.rule().getReadTransformerVersion();
        Map<String, Object> config = write
                ? scriptConfig(context, context.rule().getWriteConfig(), context.rule().getScriptCode(), "write")
                : scriptConfig(context, context.rule().getReadConfig(), context.rule().getScriptCode(), "read");
        FieldTransformer transformer = registry.require(code, version);
        if (write && !transformer.capabilities().writable() || !write && !transformer.capabilities().readable()) {
            throw new VirtualDataException("FIELD_TRANSFORM_WRITE_UNSUPPORTED", "变换器不支持请求的预览方向: " + code);
        }
        transformer.validate(definition(context, config));
        Map<String, Object> raw = write
                ? transformer.write(request.getInputs(), config)
                : transformer.read(request.getInputs(), config);
        List<FieldTransformPortEntity> targets = write ? context.physicalPorts() : context.virtualPorts();

        TransformPreviewResponse response = new TransformPreviewResponse();
        response.setTransformerCode(transformer.code());
        response.setTransformerVersion(transformer.version());
        response.setOutputs(TransformOutputMapper.normalizeEntity(raw, targets));
        return response;
    }

    public TransformLineageResponse lineage(Long virtualFieldId, Long physicalFieldMetaId) {
        if (virtualFieldId == null && physicalFieldMetaId == null) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "virtualFieldId 与 physicalFieldMetaId 至少提供一个");
        }
        List<FieldTransformPortEntity> seeds = virtualFieldId != null
                ? repository.portsByVirtualField(virtualFieldId)
                : repository.portsByPhysicalField(physicalFieldMetaId);
        TransformLineageResponse response = new TransformLineageResponse();
        for (FieldTransformPortEntity seed : seeds) {
            FieldTransformRuleEntity rule = repository.ruleById(seed.getRuleId());
            List<FieldTransformPortEntity> ports = repository.portsByRule(seed.getRuleId());
            List<FieldTransformPortEntity> physical = ports.stream().filter(item -> item.getFieldSide() == FieldSide.PHYSICAL).toList();
            List<FieldTransformPortEntity> virtual = ports.stream().filter(item -> item.getFieldSide() == FieldSide.VIRTUAL).toList();
            for (FieldTransformPortEntity source : physical) {
                for (FieldTransformPortEntity target : virtual) {
                    TransformLineageResponse.Edge edge = new TransformLineageResponse.Edge();
                    edge.setSourceType("PHYSICAL_FIELD");
                    edge.setSource(source.getPhysicalFieldMetaId() + ":" + source.getPhysicalColumnName());
                    edge.setRuleCode(rule == null ? null : rule.getRuleCode());
                    edge.setTargetType("VIRTUAL_FIELD");
                    VirtualFieldEntity field = repository.fieldById(target.getVirtualFieldId());
                    edge.setTarget(target.getVirtualFieldId() + ":" + (field == null ? "unknown" : field.getFieldCode()));
                    response.getEdges().add(edge);
                }
            }
        }
        return response;
    }

    private RuleContext context(Long ruleId) {
        FieldTransformRuleEntity rule = repository.ruleById(ruleId);
        if (rule == null) {
            throw new VirtualDataException("FIELD_NOT_MAPPED", "字段变换规则不存在: " + ruleId);
        }
        List<FieldTransformPortEntity> ports = repository.portsByRule(ruleId);
        return new RuleContext(rule,
                ports.stream().filter(item -> item.getFieldSide() == FieldSide.PHYSICAL).toList(),
                ports.stream().filter(item -> item.getFieldSide() == FieldSide.VIRTUAL).toList());
    }

    private TransformDefinition definition(RuleContext context, Map<String, Object> config) {
        return new TransformDefinition(context.rule().getRuleCode(), context.physicalPorts(), context.virtualPorts(), config);
    }

    private Map<String, Object> scriptConfig(RuleContext context, Map<String, Object> config, String scriptCode, String direction) {
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        if (config != null) value.putAll(config);
        if (scriptCode == null || scriptCode.isBlank()) return value;
        value.put("__scriptCode", scriptCode);
        value.put("__direction", direction);
        List<FieldTransformPortEntity> inputs = "read".equals(direction) ? context.physicalPorts() : context.virtualPorts();
        List<FieldTransformPortEntity> outputs = "read".equals(direction) ? context.virtualPorts() : context.physicalPorts();
        value.put("__inputAliases", aliases(inputs));
        value.put("__outputAliases", aliases(outputs));
        return value;
    }

    private Map<String, String> aliases(List<FieldTransformPortEntity> ports) {
        Map<String, String> aliases = new java.util.LinkedHashMap<>();
        for (FieldTransformPortEntity port : ports) {
            String alias = port.getFieldSide() == FieldSide.PHYSICAL
                    ? port.getPhysicalColumnName()
                    : repository.fieldById(port.getVirtualFieldId()) == null
                    ? port.getPortCode()
                    : repository.fieldById(port.getVirtualFieldId()).getFieldCode();
            aliases.put(port.getPortCode(), alias);
        }
        return aliases;
    }

    private record RuleContext(
            FieldTransformRuleEntity rule,
            List<FieldTransformPortEntity> physicalPorts,
            List<FieldTransformPortEntity> virtualPorts
    ) {
    }
}
