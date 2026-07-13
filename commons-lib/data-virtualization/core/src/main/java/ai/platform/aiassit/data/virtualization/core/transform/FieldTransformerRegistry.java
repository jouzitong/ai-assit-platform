package ai.platform.aiassit.data.virtualization.core.transform;

import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FieldTransformerRegistry {
    private final Map<String, FieldTransformer> transformers;

    public FieldTransformerRegistry(List<FieldTransformer> transformerList) {
        Map<String, FieldTransformer> index = new LinkedHashMap<>();
        for (FieldTransformer transformer : transformerList) {
            String key = key(transformer.code(), transformer.version());
            if (index.putIfAbsent(key, transformer) != null) {
                throw new IllegalStateException("字段变换器重复注册: " + key);
            }
        }
        this.transformers = Map.copyOf(index);
    }

    public FieldTransformer require(String code, Integer version) {
        if (code == null || code.isBlank()) {
            throw new VirtualDataException("FIELD_TRANSFORMER_NOT_FOUND", "字段变换器编码不能为空");
        }
        int safeVersion = version == null ? 1 : version;
        FieldTransformer transformer = transformers.get(key(code, safeVersion));
        if (transformer == null) {
            throw new VirtualDataException("FIELD_TRANSFORMER_NOT_FOUND", "字段变换器未注册: " + code + ":" + safeVersion);
        }
        return transformer;
    }

    public List<Descriptor> descriptors() {
        return transformers.values().stream()
                .sorted(Comparator.comparing(FieldTransformer::code).thenComparingInt(FieldTransformer::version))
                .map(item -> new Descriptor(item.code(), item.version(), item.capabilities()))
                .toList();
    }

    private String key(String code, int version) {
        return code + ":" + version;
    }

    public record Descriptor(String code, int version, TransformerCapabilities capabilities) {
    }
}
