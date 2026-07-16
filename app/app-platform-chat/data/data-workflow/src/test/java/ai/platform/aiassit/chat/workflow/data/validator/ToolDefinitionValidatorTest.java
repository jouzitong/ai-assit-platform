package ai.platform.aiassit.chat.workflow.data.validator;

import ai.platform.aiassit.chat.workflow.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.workflow.data.enums.ToolAdapterType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolDefinitionValidatorTest {

    private final ToolDefinitionValidator validator = new ToolDefinitionValidator();

    @Test
    void acceptsPortableHttpsBindingWithSecretReferences() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("allowedHosts", List.of("tools.example.com"));
        config.put("secretHeaders", Map.of("Authorization", "secret://tools/service-token"));

        ValidationReportDTO report = validator.validate(ToolAdapterType.HTTP,
                definition(binding("HTTP", "https://tools.example.com/run", config)));

        assertThat(report.isValid()).isTrue();
    }

    @Test
    void rejectsRawAuthorizationHeaderAndUnallowlistedHost() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("allowedHosts", List.of("other.example.com"));
        config.put("headers", Map.of("Authorization", "Bearer raw-secret"));

        ValidationReportDTO report = validator.validate(ToolAdapterType.HTTP,
                definition(binding("HTTP", "https://tools.example.com/run", config)));

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(message -> message.contains("allowedHosts"));
        assertThat(report.getErrors()).anyMatch(message -> message.contains("forbidden header")
                || message.contains("secret field"));
    }

    @Test
    void failsClosedForUnsupportedMcpBinding() {
        ValidationReportDTO report = validator.validate(ToolAdapterType.MCP,
                definition(binding("MCP", "https://mcp.example.com", Map.of(
                        "allowedHosts", List.of("mcp.example.com")))));

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(message -> message.contains("not supported"));
    }

    private Map<String, Object> definition(Map<String, Object> binding) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("inputSchema", Map.of("type", "object"));
        definition.put("outputSchema", Map.of("type", "object"));
        definition.put("permissionPolicy", Map.of());
        definition.put("approvalPolicy", Map.of("required", false));
        definition.put("timeoutMs", 30_000);
        definition.put("bindings", List.of(binding));
        return definition;
    }

    private Map<String, Object> binding(String type, String endpoint, Map<String, Object> config) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("bindingType", type);
        binding.put("endpointRef", endpoint);
        binding.put("enabled", true);
        binding.put("secretRefs", List.of());
        binding.put("config", config);
        return binding;
    }
}
