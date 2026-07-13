package ai.platform.aiassit.data.virtualization.core.transform;

import java.util.Map;

public interface FieldTransformer {
    String code();
    int version();
    TransformerCapabilities capabilities();
    void validate(TransformDefinition definition);
    Map<String, Object> read(Map<String, Object> physicalPorts, Map<String, Object> config);
    Map<String, Object> write(Map<String, Object> virtualPorts, Map<String, Object> config);
}
