package ai.platform.aiassit.data.virtualization.core.transform;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import ai.platform.aiassit.data.virtualization.core.transform.builtin.EnumMapFieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.builtin.JsonExtractFieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.builtin.JsonComposeFieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.builtin.TextConcatFieldTransformer;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformPortEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldTransformerTest {

    @Test
    void shouldSplitOneJsonFieldIntoMultipleVirtualFields() {
        JsonExtractFieldTransformer transformer = new JsonExtractFieldTransformer(new ObjectMapper());
        Map<String, Object> config = Map.of(
                "configVersion", 1,
                "outputPaths", Map.of("email", "$.email", "mobile", "$.mobile")
        );
        transformer.validate(new TransformDefinition("contact", List.of(port(FieldSide.PHYSICAL, "source")),
                List.of(port(FieldSide.VIRTUAL, "email"), port(FieldSide.VIRTUAL, "mobile")), config));

        Map<String, Object> result = transformer.read(Map.of("source", "{\"email\":\"a@b.com\",\"mobile\":\"138\"}"), config);

        assertEquals("a@b.com", result.get("email"));
        assertEquals("138", result.get("mobile"));
    }

    @Test
    void shouldMergeMultiplePhysicalFieldsIntoOneVirtualField() {
        TextConcatFieldTransformer transformer = new TextConcatFieldTransformer();
        Map<String, Object> config = Map.of(
                "configVersion", 1,
                "inputPorts", List.of("first", "last"),
                "outputPort", "fullName",
                "delimiter", " "
        );
        transformer.validate(new TransformDefinition("name", List.of(port(FieldSide.PHYSICAL, "first"), port(FieldSide.PHYSICAL, "last")),
                List.of(port(FieldSide.VIRTUAL, "fullName")), config));

        assertEquals("Ada Lovelace", transformer.read(Map.of("first", "Ada", "last", "Lovelace"), config).get("fullName"));
    }

    @Test
    void shouldProjectSamePhysicalValueIntoDifferentSemantics() {
        EnumMapFieldTransformer transformer = new EnumMapFieldTransformer();
        Map<String, Object> orderConfig = Map.of("configVersion", 1, "outputPort", "orderStatus", "mappings", Map.of("1", "PAID"));
        Map<String, Object> riskConfig = Map.of("configVersion", 1, "outputPort", "riskFlag", "mappings", Map.of("1", "NORMAL"));

        assertEquals("PAID", transformer.read(Map.of("legacy", 1), orderConfig).get("orderStatus"));
        assertEquals("NORMAL", transformer.read(Map.of("legacy", 1), riskConfig).get("riskFlag"));
    }

    @Test
    void shouldComposeVirtualFieldsIntoJsonTextForDatabaseWrite() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonComposeFieldTransformer transformer = new JsonComposeFieldTransformer(objectMapper);
        Map<String, Object> config = Map.of(
                "configVersion", 1,
                "inputPaths", Map.of("email", "$.email", "mobile", "$.mobile"),
                "outputPort", "contact"
        );
        TransformDefinition definition = new TransformDefinition("contact", List.of(port(FieldSide.PHYSICAL, "contact")),
                List.of(port(FieldSide.VIRTUAL, "email"), port(FieldSide.VIRTUAL, "mobile")), config);

        transformer.validate(definition);
        Map<String, Object> output = transformer.write(Map.of("email", "a@b.com", "mobile", "138"), config);

        assertEquals(Map.of("email", "a@b.com", "mobile", "138"),
                objectMapper.readValue(String.valueOf(output.get("contact")), Map.class));
    }

    @Test
    void shouldRejectTransformerConfigWithUnknownPort() {
        TextConcatFieldTransformer transformer = new TextConcatFieldTransformer();
        Map<String, Object> config = Map.of(
                "configVersion", 1,
                "inputPorts", List.of("missing"),
                "outputPort", "fullName"
        );
        TransformDefinition definition = new TransformDefinition("name", List.of(port(FieldSide.PHYSICAL, "first")),
                List.of(port(FieldSide.VIRTUAL, "fullName")), config);

        assertThrows(VirtualDataException.class, () -> transformer.validate(definition));
    }

    @Test
    void shouldRejectAmbiguousEnumReverseMapping() {
        EnumMapFieldTransformer transformer = new EnumMapFieldTransformer();
        Map<String, Object> config = Map.of(
                "configVersion", 1,
                "outputPort", "legacy",
                "mappings", Map.of("1", "ACTIVE", "2", "ACTIVE")
        );

        assertThrows(VirtualDataException.class, () -> transformer.write(Map.of("status", "ACTIVE"), config));
    }

    private FieldTransformPortEntity port(FieldSide side, String code) {
        FieldTransformPortEntity port = new FieldTransformPortEntity();
        port.setFieldSide(side);
        port.setPortCode(code);
        return port;
    }
}
