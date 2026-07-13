package ai.platform.aiassit.data.virtualization.core.transform.builtin;

import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.transform.ConfigSupport;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.TransformDefinition;
import ai.platform.aiassit.data.virtualization.core.transform.TransformerCapabilities;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JsonExtractFieldTransformer implements FieldTransformer {
    private final ObjectMapper objectMapper;

    public JsonExtractFieldTransformer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override public String code() { return "json_extract"; }
    @Override public int version() { return 1; }
    @Override public TransformerCapabilities capabilities() { return TransformerCapabilities.readOnlyLocal(); }

    @Override
    public void validate(TransformDefinition definition) {
        if (definition.physicalPorts().size() != 1 || definition.virtualPorts().isEmpty()) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "json_extract 要求一个物理端口和至少一个虚拟端口");
        }
        ConfigSupport.rejectUnknown(definition.config(), "configVersion", "inputPort", "outputPaths", "nullHandling");
        Map<String, Object> paths = ConfigSupport.objectMap(definition.config(), "outputPaths");
        Set<String> virtualPorts = definition.virtualPorts().stream().map(item -> item.getPortCode()).collect(Collectors.toSet());
        if (!paths.keySet().equals(virtualPorts)) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "outputPaths 必须精确覆盖虚拟端口: " + virtualPorts);
        }
        String inputPort = ConfigSupport.string(definition.config(), "inputPort", definition.physicalPorts().get(0).getPortCode());
        ConfigSupport.requirePortReferences(List.of(inputPort), ConfigSupport.portCodes(definition.physicalPorts()), "inputPort", false);
        String nullHandling = ConfigSupport.string(definition.config(), "nullHandling", "KEEP_NULL");
        if (!"KEEP_NULL".equals(nullHandling)) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "json_extract 首期仅支持 nullHandling=KEEP_NULL");
        }
        paths.values().forEach(path -> validatePath(String.valueOf(path)));
    }

    @Override
    public Map<String, Object> read(Map<String, Object> physicalPorts, Map<String, Object> config) {
        Object source = physicalPorts.get(ConfigSupport.string(config, "inputPort", ""));
        if (source == null) {
            source = ConfigSupport.firstValue(physicalPorts);
        }
        Map<String, Object> output = new LinkedHashMap<>();
        Map<String, Object> paths = ConfigSupport.objectMap(config, "outputPaths");
        if (source == null) {
            paths.keySet().forEach(key -> output.put(key, null));
            return output;
        }
        try {
            JsonNode root = source instanceof String text ? objectMapper.readTree(text) : objectMapper.valueToTree(source);
            paths.forEach((port, path) -> {
                JsonNode node = resolve(root, String.valueOf(path));
                output.put(port, node == null || node.isNull() || node.isMissingNode() ? null : objectMapper.convertValue(node, Object.class));
            });
            return output;
        } catch (Exception ex) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "JSON 字段拆分失败", ex);
        }
    }

    @Override
    public Map<String, Object> write(Map<String, Object> virtualPorts, Map<String, Object> config) {
        throw new VirtualDataException("FIELD_TRANSFORM_WRITE_UNSUPPORTED", "json_extract 不支持写回，请配置 json_compose");
    }

    private JsonNode resolve(JsonNode root, String path) {
        JsonNode current = root;
        String normalized = path.substring(2);
        if (normalized.isBlank()) {
            return current;
        }
        for (String part : normalized.split("\\.")) {
            current = current.path(part);
        }
        return current;
    }

    private void validatePath(String path) {
        if (path == null || !(path.equals("$") || path.matches("\\$\\.[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*"))) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "仅支持受限 JSON 属性路径: " + path);
        }
    }
}
