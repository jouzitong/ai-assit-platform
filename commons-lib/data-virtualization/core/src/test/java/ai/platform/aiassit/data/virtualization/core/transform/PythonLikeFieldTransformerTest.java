package ai.platform.aiassit.data.virtualization.core.transform;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import ai.platform.aiassit.data.virtualization.core.transform.builtin.PythonLikeFieldTransformer;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformPortEntity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PythonLikeFieldTransformerTest {
    @Test
    void shouldSplitOnePhysicalValueIntoMultipleVirtualFields() {
        PythonLikeFieldTransformer transformer = new PythonLikeFieldTransformer();
        FieldTransformPortEntity physical = port(FieldSide.PHYSICAL, "physical", "a", null);
        FieldTransformPortEntity virtualOne = port(FieldSide.VIRTUAL, "virtual1", null, 11L);
        FieldTransformPortEntity virtualTwo = port(FieldSide.VIRTUAL, "virtual2", null, 12L);
        Map<String, Object> config = config("read", "a", "vb_f_1", "vb_f_2");
        config.put("__scriptCode", """
                def read(inputs, context):
                    value = inputs.get("a")
                    return {
                        "vb_f_1": value if value in [1, 2, 3] else None,
                        "vb_f_2": value if value in [4, 5, 6] else None,
                    }

                def write(inputs, context):
                    return {"a": inputs.get("vb_f_1")}
                """);
        transformer.validate(new TransformDefinition("split", List.of(physical), List.of(virtualOne, virtualTwo), config));

        Map<String, Object> output = transformer.read(Map.of("physical", 4L), config);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("virtual1", null);
        expected.put("virtual2", 4L);
        assertEquals(expected, output);
    }

    @Test
    void shouldSupportPartialWriteByCheckingInputPresence() {
        PythonLikeFieldTransformer transformer = new PythonLikeFieldTransformer();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("__direction", "write");
        config.put("__inputAliases", Map.of("virtual1", "vb_f_1", "virtual2", "vb_f_2"));
        config.put("__outputAliases", Map.of("physical", "a"));
        config.put("__scriptCode", """
                def read(inputs, context):
                    return {"vb_f_1": inputs.get("a")}

                def write(inputs, context):
                    if "vb_f_2" in inputs:
                        return {"a": inputs.get("vb_f_2")}
                    return {"a": inputs.get("vb_f_1")}
                """);

        assertEquals(Map.of("physical", 6), transformer.write(Map.of("virtual2", 6), config));
    }

    private Map<String, Object> config(String direction, String input, String... outputs) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("__direction", direction);
        config.put("__inputAliases", Map.of("physical", input));
        Map<String, String> outputAliases = new LinkedHashMap<>();
        for (int i = 0; i < outputs.length; i++) outputAliases.put("virtual" + (i + 1), outputs[i]);
        config.put("__outputAliases", outputAliases);
        return config;
    }

    private FieldTransformPortEntity port(FieldSide side, String code, String column, Long virtualFieldId) {
        FieldTransformPortEntity port = new FieldTransformPortEntity();
        port.setFieldSide(side);
        port.setPortCode(code);
        port.setPhysicalColumnName(column);
        port.setVirtualFieldId(virtualFieldId);
        return port;
    }
}
