package ai.platform.aiassit.data.virtualization.core.transform.builtin;

import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.transform.ConfigSupport;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.TransformDefinition;
import ai.platform.aiassit.data.virtualization.core.transform.TransformerCapabilities;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TextConcatFieldTransformer implements FieldTransformer {
    @Override public String code() { return "text_concat"; }
    @Override public int version() { return 1; }
    @Override public TransformerCapabilities capabilities() { return TransformerCapabilities.readOnlyLocal(); }

    @Override
    public void validate(TransformDefinition definition) {
        if (definition.physicalPorts().isEmpty() || definition.virtualPorts().size() != 1) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "text_concat 要求至少一个物理端口和一个虚拟端口");
        }
        ConfigSupport.rejectUnknown(definition.config(), "configVersion", "inputPorts", "outputPort", "delimiter", "nullHandling");
        List<String> inputs = ConfigSupport.stringList(definition.config(), "inputPorts");
        if (inputs.isEmpty()) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "text_concat 缺少 inputPorts");
        }
        ConfigSupport.requirePortReferences(inputs, ConfigSupport.portCodes(definition.physicalPorts()), "inputPorts", true);
        String outputPort = ConfigSupport.string(definition.config(), "outputPort", definition.virtualPorts().get(0).getPortCode());
        ConfigSupport.requirePortReferences(List.of(outputPort), ConfigSupport.portCodes(definition.virtualPorts()), "outputPort", true);
        String nullHandling = ConfigSupport.string(definition.config(), "nullHandling", "SKIP");
        if (!"SKIP".equals(nullHandling)) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "text_concat 首期仅支持 nullHandling=SKIP");
        }
    }

    @Override
    public Map<String, Object> read(Map<String, Object> physicalPorts, Map<String, Object> config) {
        List<String> parts = new ArrayList<>();
        for (String input : ConfigSupport.stringList(config, "inputPorts")) {
            Object value = physicalPorts.get(input);
            if (value != null) {
                parts.add(String.valueOf(value));
            }
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put(ConfigSupport.string(config, "outputPort", "value"),
                String.join(ConfigSupport.string(config, "delimiter", ""), parts));
        return output;
    }

    @Override
    public Map<String, Object> write(Map<String, Object> virtualPorts, Map<String, Object> config) {
        throw new VirtualDataException("FIELD_TRANSFORM_WRITE_UNSUPPORTED", "text_concat 不支持写回，请显式配置 text_split");
    }
}
