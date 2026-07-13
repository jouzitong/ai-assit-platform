package ai.platform.aiassit.data.virtualization.core.transform.builtin;

import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.transform.ConfigSupport;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.TransformDefinition;
import ai.platform.aiassit.data.virtualization.core.transform.TransformerCapabilities;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JsonComposeFieldTransformer implements FieldTransformer {
    private final ObjectMapper objectMapper;

    public JsonComposeFieldTransformer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override public String code() { return "json_compose"; }
    @Override public int version() { return 1; }
    @Override public TransformerCapabilities capabilities() {
        return new TransformerCapabilities(false, true, false, false, false, false, true);
    }

    @Override
    public void validate(TransformDefinition definition) {
        if (definition.physicalPorts().size() != 1 || definition.virtualPorts().isEmpty()) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "json_compose 要求一个物理端口和至少一个虚拟端口");
        }
        ConfigSupport.rejectUnknown(definition.config(), "configVersion", "inputPaths", "outputPort");
        Map<String, Object> mappings = ConfigSupport.objectMap(definition.config(), "inputPaths");
        if (mappings.isEmpty()) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "json_compose 缺少 inputPaths");
        }
        ConfigSupport.requirePortReferences(mappings.keySet(), ConfigSupport.portCodes(definition.virtualPorts()), "inputPaths", true);
        String outputPort = ConfigSupport.string(definition.config(), "outputPort", definition.physicalPorts().get(0).getPortCode());
        ConfigSupport.requirePortReferences(java.util.List.of(outputPort), ConfigSupport.portCodes(definition.physicalPorts()), "outputPort", true);
        mappings.values().forEach(path -> validatePath(String.valueOf(path)));
    }

    @Override
    public Map<String, Object> read(Map<String, Object> physicalPorts, Map<String, Object> config) {
        throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "json_compose 只用于写回");
    }

    @Override
    public Map<String, Object> write(Map<String, Object> virtualPorts, Map<String, Object> config) {
        Map<String, Object> json = new LinkedHashMap<>();
        ConfigSupport.objectMap(config, "inputPaths").forEach((port, path) -> putPath(json, String.valueOf(path), virtualPorts.get(port)));
        Map<String, Object> output = new LinkedHashMap<>();
        try {
            output.put(ConfigSupport.string(config, "outputPort", "value"), objectMapper.writeValueAsString(json));
        } catch (JsonProcessingException ex) {
            throw new VirtualDataException("FIELD_TRANSFORM_WRITE_UNSUPPORTED", "JSON 字段组合失败", ex);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    private void putPath(Map<String, Object> root, String path, Object value) {
        validatePath(path);
        String[] parts = path.substring(2).split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(parts[i], key -> new LinkedHashMap<>());
        }
        current.put(parts[parts.length - 1], value);
    }

    private void validatePath(String path) {
        if (!path.matches("\\$\\.[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "仅支持受限 JSON 属性路径: " + path);
        }
    }
}
