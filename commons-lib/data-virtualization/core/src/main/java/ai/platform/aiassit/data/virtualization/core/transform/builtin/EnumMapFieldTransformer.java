package ai.platform.aiassit.data.virtualization.core.transform.builtin;

import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.transform.ConfigSupport;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.TransformDefinition;
import ai.platform.aiassit.data.virtualization.core.transform.TransformerCapabilities;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class EnumMapFieldTransformer implements FieldTransformer {
    @Override public String code() { return "enum_map"; }
    @Override public int version() { return 1; }
    @Override public TransformerCapabilities capabilities() {
        return new TransformerCapabilities(true, true, true, false, false, false, true);
    }

    @Override
    public void validate(TransformDefinition definition) {
        ConfigSupport.requirePortCount(definition, 1, 1);
        ConfigSupport.rejectUnknown(definition.config(), "configVersion", "inputPort", "outputPort", "mappings", "defaultValue");
        if (ConfigSupport.objectMap(definition.config(), "mappings").isEmpty()) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "enum_map 缺少 mappings");
        }
        String inputPort = ConfigSupport.string(definition.config(), "inputPort", definition.physicalPorts().get(0).getPortCode());
        String outputPort = ConfigSupport.string(definition.config(), "outputPort", definition.virtualPorts().get(0).getPortCode());
        boolean readShape = ConfigSupport.portCodes(definition.physicalPorts()).contains(inputPort)
                && ConfigSupport.portCodes(definition.virtualPorts()).contains(outputPort);
        boolean writeShape = ConfigSupport.portCodes(definition.virtualPorts()).contains(inputPort)
                && ConfigSupport.portCodes(definition.physicalPorts()).contains(outputPort);
        if (!readShape && !writeShape) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "enum_map inputPort/outputPort 与读写端口不匹配");
        }
    }

    @Override
    public Map<String, Object> read(Map<String, Object> physicalPorts, Map<String, Object> config) {
        return map(physicalPorts, config, false);
    }

    @Override
    public Map<String, Object> write(Map<String, Object> virtualPorts, Map<String, Object> config) {
        return map(virtualPorts, config, true);
    }

    private Map<String, Object> map(Map<String, Object> input, Map<String, Object> config, boolean reverse) {
        Object value = ConfigSupport.firstValue(input);
        Map<String, Object> mappings = ConfigSupport.objectMap(config, "mappings");
        Object mapped;
        if (reverse) {
            java.util.List<String> matches = mappings.entrySet().stream()
                    .filter(entry -> java.util.Objects.equals(entry.getValue(), value))
                    .map(Map.Entry::getKey)
                    .toList();
            if (matches.size() != 1) {
                throw new VirtualDataException("FIELD_TRANSFORM_WRITE_UNSUPPORTED",
                        "枚举值必须存在唯一反向映射: " + value + ", 实际数量=" + matches.size());
            }
            mapped = matches.get(0);
        } else {
            mapped = mappings.getOrDefault(String.valueOf(value), config == null ? null : config.get("defaultValue"));
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put(ConfigSupport.string(config, "outputPort", "value"), mapped);
        return output;
    }
}
