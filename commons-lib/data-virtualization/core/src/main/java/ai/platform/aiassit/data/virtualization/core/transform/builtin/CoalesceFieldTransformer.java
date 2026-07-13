package ai.platform.aiassit.data.virtualization.core.transform.builtin;

import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.transform.ConfigSupport;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.TransformDefinition;
import ai.platform.aiassit.data.virtualization.core.transform.TransformerCapabilities;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CoalesceFieldTransformer implements FieldTransformer {
    @Override public String code() { return "coalesce"; }
    @Override public int version() { return 1; }
    @Override public TransformerCapabilities capabilities() { return TransformerCapabilities.readOnlyLocal(); }

    @Override
    public void validate(TransformDefinition definition) {
        if (definition.physicalPorts().isEmpty() || definition.virtualPorts().size() != 1) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "coalesce 要求至少一个物理端口和一个虚拟端口");
        }
        ConfigSupport.rejectUnknown(definition.config(), "configVersion", "inputPorts", "outputPort");
        List<String> inputs = ConfigSupport.stringList(definition.config(), "inputPorts");
        if (!inputs.isEmpty()) {
            ConfigSupport.requirePortReferences(inputs, ConfigSupport.portCodes(definition.physicalPorts()), "inputPorts", false);
        }
        String outputPort = ConfigSupport.string(definition.config(), "outputPort", definition.virtualPorts().get(0).getPortCode());
        ConfigSupport.requirePortReferences(List.of(outputPort), ConfigSupport.portCodes(definition.virtualPorts()), "outputPort", true);
    }

    @Override
    public Map<String, Object> read(Map<String, Object> physicalPorts, Map<String, Object> config) {
        List<String> ports = ConfigSupport.stringList(config, "inputPorts");
        Object value = (ports.isEmpty() ? physicalPorts.keySet().stream().toList() : ports).stream()
                .map(physicalPorts::get).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put(ConfigSupport.string(config, "outputPort", "value"), value);
        return output;
    }

    @Override
    public Map<String, Object> write(Map<String, Object> virtualPorts, Map<String, Object> config) {
        throw new VirtualDataException("FIELD_TRANSFORM_WRITE_UNSUPPORTED", "coalesce 不支持写回");
    }
}
