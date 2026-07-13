package ai.platform.aiassit.data.virtualization.core.transform;

import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConfigSupport {
    private ConfigSupport() {
    }

    public static void rejectUnknown(Map<String, Object> config, String... allowed) {
        if (config == null || config.isEmpty()) {
            return;
        }
        Set<String> allowSet = Set.of(allowed);
        List<String> unknown = config.keySet().stream().filter(key -> !allowSet.contains(key)).toList();
        if (!unknown.isEmpty()) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "变换配置包含未知字段: " + unknown);
        }
        Object version = config.get("configVersion");
        if (version == null) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "变换配置缺少 configVersion");
        }
        try {
            if (Integer.parseInt(String.valueOf(version)) != 1) {
                throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "暂不支持的变换配置版本: " + version);
            }
        } catch (NumberFormatException ex) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "configVersion 必须为整数", ex);
        }
    }

    public static String string(Map<String, Object> config, String key, String defaultValue) {
        Object value = config == null ? null : config.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> objectMap(Map<String, Object> config, String key) {
        Object value = config == null ? null : config.get(key);
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (!(value instanceof Map<?, ?> source)) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", key + " 必须是 JSON 对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((mapKey, mapValue) -> result.put(String.valueOf(mapKey), mapValue));
        return result;
    }

    public static List<String> stringList(Map<String, Object> config, String key) {
        Object value = config == null ? null : config.get(key);
        if (value == null) {
            return new ArrayList<>();
        }
        if (!(value instanceof Collection<?> source)) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", key + " 必须是数组");
        }
        return source.stream().map(String::valueOf).toList();
    }

    public static Object firstValue(Map<String, Object> values) {
        return values == null || values.isEmpty() ? null : values.values().iterator().next();
    }

    public static void requirePortCount(TransformDefinition definition, int physical, int virtual) {
        if (definition.physicalPorts().size() != physical || definition.virtualPorts().size() != virtual) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID",
                    definition.ruleCode() + " 端口数量不合法，要求 physical=" + physical + ", virtual=" + virtual);
        }
    }

    public static void requirePortReferences(
            Collection<String> configured,
            Collection<String> declared,
            String configKey,
            boolean exact
    ) {
        if (configured != null && configured.stream().anyMatch(java.util.Objects::isNull)
                || declared != null && declared.stream().anyMatch(java.util.Objects::isNull)) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", configKey + " 端口编码不能为 null");
        }
        Set<String> configuredSet = configured == null ? Set.of() : Set.copyOf(configured);
        Set<String> declaredSet = declared == null ? Set.of() : Set.copyOf(declared);
        boolean valid = exact ? configuredSet.equals(declaredSet) : declaredSet.containsAll(configuredSet);
        if (!valid) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID",
                    configKey + " 引用的端口不合法，configured=" + configuredSet + ", declared=" + declaredSet);
        }
    }

    public static List<String> portCodes(Collection<ai.platform.aiassit.data.virtualization.data.entity.FieldTransformPortEntity> ports) {
        if (ports == null || ports.stream().anyMatch(port -> port == null || port.getPortCode() == null)) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "字段变换端口及端口编码不能为空");
        }
        return ports.stream().map(ai.platform.aiassit.data.virtualization.data.entity.FieldTransformPortEntity::getPortCode)
                .collect(Collectors.toList());
    }
}
