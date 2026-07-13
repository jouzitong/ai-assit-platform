package ai.platform.aiassit.data.virtualization.core.transform.builtin;

import ai.platform.aiassit.data.virtualization.core.transform.ConfigSupport;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.TransformDefinition;
import ai.platform.aiassit.data.virtualization.core.transform.TransformerCapabilities;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class IdentityFieldTransformer implements FieldTransformer {
    @Override public String code() { return "identity"; }
    @Override public int version() { return 1; }
    @Override public TransformerCapabilities capabilities() { return TransformerCapabilities.identity(); }

    @Override
    public void validate(TransformDefinition definition) {
        ConfigSupport.requirePortCount(definition, 1, 1);
        ConfigSupport.rejectUnknown(definition.config(), "configVersion");
    }

    @Override
    public Map<String, Object> read(Map<String, Object> physicalPorts, Map<String, Object> config) {
        return single(physicalPorts);
    }

    @Override
    public Map<String, Object> write(Map<String, Object> virtualPorts, Map<String, Object> config) {
        return single(virtualPorts);
    }

    private Map<String, Object> single(Map<String, Object> input) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (input != null && !input.isEmpty()) {
            output.put(input.keySet().iterator().next(), ConfigSupport.firstValue(input));
        }
        return output;
    }
}
