package ai.platform.aiassit.data.virtualization.core.transform.builtin;

import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.transform.ConfigSupport;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.TransformDefinition;
import ai.platform.aiassit.data.virtualization.core.transform.TransformerCapabilities;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TypeCastFieldTransformer implements FieldTransformer {
    private final ObjectMapper objectMapper;

    public TypeCastFieldTransformer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override public String code() { return "type_cast"; }
    @Override public int version() { return 1; }
    @Override public TransformerCapabilities capabilities() {
        return new TransformerCapabilities(true, true, true, false, false, false, true);
    }

    @Override
    public void validate(TransformDefinition definition) {
        ConfigSupport.requirePortCount(definition, 1, 1);
        ConfigSupport.rejectUnknown(definition.config(), "configVersion", "targetType", "sourceType");
        if (ConfigSupport.string(definition.config(), "targetType", "").isBlank()
                && ConfigSupport.string(definition.config(), "sourceType", "").isBlank()) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "type_cast 缺少 targetType/sourceType");
        }
    }

    @Override
    public Map<String, Object> read(Map<String, Object> physicalPorts, Map<String, Object> config) {
        return cast(physicalPorts, ConfigSupport.string(config, "targetType", "STRING"));
    }

    @Override
    public Map<String, Object> write(Map<String, Object> virtualPorts, Map<String, Object> config) {
        return cast(virtualPorts, ConfigSupport.string(config, "sourceType", ConfigSupport.string(config, "targetType", "STRING")));
    }

    private Map<String, Object> cast(Map<String, Object> input, String type) {
        Object value = ConfigSupport.firstValue(input);
        Object result;
        if (value == null) {
            result = null;
        } else {
            try {
                result = switch (type.toUpperCase()) {
                    case "STRING" -> String.valueOf(value);
                    case "BOOLEAN" -> value instanceof Boolean bool ? bool : Boolean.valueOf(String.valueOf(value));
                    case "INTEGER" -> value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
                    case "LONG" -> value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
                    case "DECIMAL" -> value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
                    case "DATE" -> value instanceof LocalDate date ? date : LocalDate.parse(String.valueOf(value));
                    case "TIMESTAMP" -> value instanceof OffsetDateTime time ? time : OffsetDateTime.parse(String.valueOf(value));
                    case "JSON" -> value instanceof String text ? objectMapper.readValue(text, Object.class) : value;
                    default -> throw new VirtualDataException("TYPE_CONVERSION_UNSUPPORTED", "不支持的目标类型: " + type);
                };
            } catch (VirtualDataException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new VirtualDataException("TYPE_CONVERSION_UNSUPPORTED", "字段类型转换失败: " + type, ex);
            }
        }
        Map<String, Object> output = new LinkedHashMap<>();
        if (input != null && !input.isEmpty()) {
            output.put(input.keySet().iterator().next(), result);
        }
        return output;
    }
}
