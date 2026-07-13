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
import java.util.regex.Pattern;

@Component
public class TextSplitFieldTransformer implements FieldTransformer {
    @Override public String code() { return "text_split"; }
    @Override public int version() { return 1; }
    @Override public TransformerCapabilities capabilities() {
        return new TransformerCapabilities(false, true, false, false, false, false, true);
    }

    @Override
    public void validate(TransformDefinition definition) {
        if (definition.virtualPorts().size() != 1 || definition.physicalPorts().isEmpty()) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "text_split 要求一个虚拟端口和至少一个物理端口");
        }
        ConfigSupport.rejectUnknown(definition.config(), "configVersion", "inputPort", "outputPorts", "delimiter");
        List<String> outputPorts = ConfigSupport.stringList(definition.config(), "outputPorts");
        ConfigSupport.requirePortReferences(outputPorts, ConfigSupport.portCodes(definition.physicalPorts()), "outputPorts", true);
        String inputPort = ConfigSupport.string(definition.config(), "inputPort", definition.virtualPorts().get(0).getPortCode());
        ConfigSupport.requirePortReferences(List.of(inputPort), ConfigSupport.portCodes(definition.virtualPorts()), "inputPort", true);
        if (ConfigSupport.string(definition.config(), "delimiter", " ").isEmpty()) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "text_split delimiter 不能为空");
        }
    }

    @Override
    public Map<String, Object> read(Map<String, Object> physicalPorts, Map<String, Object> config) {
        throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "text_split 只用于写回");
    }

    @Override
    public Map<String, Object> write(Map<String, Object> virtualPorts, Map<String, Object> config) {
        Object input = virtualPorts.get(ConfigSupport.string(config, "inputPort", ""));
        if (input == null) {
            input = ConfigSupport.firstValue(virtualPorts);
        }
        String delimiter = ConfigSupport.string(config, "delimiter", " ");
        String[] parts = input == null ? new String[0] : String.valueOf(input).split(Pattern.quote(delimiter), -1);
        List<String> outputs = ConfigSupport.stringList(config, "outputPorts");
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < outputs.size(); i++) {
            result.put(outputs.get(i), i < parts.length ? parts[i] : null);
        }
        return result;
    }
}
