package ai.platform.aiassit.chat.agent.control.data.validator;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.agent.control.data.enums.ToolAdapterType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates Tool schema, permission/approval policy and runtime bindings without resolving secrets. */
@Component
public class ToolDefinitionValidator {

    private static final Set<String> BINDING_TYPES = Set.of(
            "HTTP", "MCP", "JAVA_INTERNAL", "HOSTED", "PYTHON_MODULE", "JAVASCRIPT_MODULE");
    private static final Set<String> IMPLEMENTATION_RUNTIMES = Set.of("PYTHON", "JAVASCRIPT");
    private static final Set<String> AGENT_RUNTIMES = Set.of(
            "OPENAI_AGENTS_PYTHON", "OPENAI_AGENTS_TYPESCRIPT");
    private static final Pattern PYTHON_ENTRYPOINT = Pattern.compile(
            "(?m)^\\s*(?:async\\s+)?def\\s+run\\s*\\(");
    private static final Pattern JAVASCRIPT_ENTRYPOINT = Pattern.compile(
            "(?m)^\\s*export\\s+(?:async\\s+)?function\\s+run\\s*\\(");
    private static final Set<String> SECRET_KEYS = Set.of(
            "apikey", "token", "accesstoken", "refreshtoken", "password", "secret",
            "clientsecret", "authorization", "credential", "credentials", "cookie");

    public ValidationReportDTO validate(ToolAdapterType ignoredAdapterType, Map<String, Object> definition) {
        ValidationReportDTO report = new ValidationReportDTO();
        if (definition == null) {
            report.error("definition is required");
            report.finish();
            return report;
        }
        if (!(definition.get("inputSchema") instanceof Map<?, ?>)) {
            report.error("definition.inputSchema must be a JSON object");
        }
        if (!(definition.get("outputSchema") instanceof Map<?, ?>)) {
            report.error("definition.outputSchema must be a JSON object");
        }
        if (!(definition.get("permissionPolicy") instanceof Map<?, ?>)) {
            report.error("definition.permissionPolicy must be a JSON object");
        }
        if (!(definition.get("approvalPolicy") instanceof Map<?, ?>)) {
            report.error("definition.approvalPolicy must be a JSON object");
        }
        Integer timeoutMs = integerValue(definition.get("timeoutMs"));
        if (timeoutMs == null || timeoutMs < 100 || timeoutMs > 900_000) {
            report.error("definition.timeoutMs must be between 100 and 900000");
        }
        if ("MANAGED_CODE".equals(text(definition.get("executionMode")))) {
            validateManagedCode(definition, report);
        } else {
            Object bindings = definition.get("bindings");
            if (!(bindings instanceof Collection<?> values) || values.isEmpty()) {
                report.error("definition.bindings must contain at least one runtime binding");
                rejectEmbeddedSecrets(definition, "definition", report);
                report.finish();
                return report;
            }
            int index = 0;
            for (Object value : values) {
                if (!(value instanceof Map<?, ?> raw)) {
                    report.error("definition.bindings[" + index + "] must be an object");
                } else {
                    validateBinding(cast(raw), index, report);
                }
                index++;
            }
        }
        rejectEmbeddedSecrets(definition, "definition", report);
        report.finish();
        return report;
    }

    private void validateManagedCode(Map<String, Object> definition, ValidationReportDTO report) {
        String runtime = text(definition.get("implementationRuntime"));
        if (runtime != null) runtime = runtime.toUpperCase(Locale.ROOT);
        if (!IMPLEMENTATION_RUNTIMES.contains(runtime)) {
            report.error("definition.implementationRuntime must be PYTHON or JAVASCRIPT");
        }
        String sourceCode = text(definition.get("sourceCode"));
        if (!StringUtils.hasText(sourceCode)) {
            report.error("definition.sourceCode is required");
        } else if (sourceCode.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 524_288) {
            report.error("definition.sourceCode exceeds 524288 bytes");
        } else if ("PYTHON".equals(runtime) && !PYTHON_ENTRYPOINT.matcher(sourceCode).find()) {
            report.error("Python Tool must define run(arguments, context)");
        } else if ("JAVASCRIPT".equals(runtime) && !JAVASCRIPT_ENTRYPOINT.matcher(sourceCode).find()) {
            report.error("JavaScript Tool must export run(args, context)");
        }
        Object compatible = definition.get("compatibleAgentRuntimes");
        if (!(compatible instanceof Collection<?> values) || values.isEmpty()) {
            report.error("definition.compatibleAgentRuntimes must contain at least one Agent runtime");
        } else {
            for (Object value : values) {
                if (!AGENT_RUNTIMES.contains(String.valueOf(value).trim().toUpperCase(Locale.ROOT))) {
                    report.error("definition.compatibleAgentRuntimes contains an unsupported runtime");
                }
            }
        }
        if (!(definition.get("runtimeConfig") instanceof Map<?, ?>)) {
            report.error("definition.runtimeConfig must be a JSON object");
        }
    }

    private void validateBinding(Map<String, Object> binding, int index, ValidationReportDTO report) {
        String type = text(binding.get("bindingType"));
        type = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (!BINDING_TYPES.contains(type)) {
            report.error("definition.bindings[" + index + "].bindingType is unsupported");
            return;
        }
        if (Set.of("HTTP", "MCP", "JAVA_INTERNAL").contains(type)
                && !StringUtils.hasText(text(binding.get("endpointRef")))) {
            report.error("definition.bindings[" + index + "].endpointRef is required for " + type);
        }
        if (Set.of("MCP", "HOSTED", "PYTHON_MODULE", "JAVASCRIPT_MODULE").contains(type)) {
            report.error("definition.bindings[" + index + "] is not supported by the deployed portable Tool Gateway: "
                    + type);
        }
        if (Set.of("HTTP", "JAVA_INTERNAL").contains(type)) {
            validatePortableHttpBinding(binding, index, report);
        }
        if (Set.of("PYTHON_MODULE", "JAVASCRIPT_MODULE").contains(type)) {
            if (!StringUtils.hasText(text(binding.get("packageUri")))) {
                report.error("definition.bindings[" + index + "].packageUri is required for " + type);
            }
            if (!StringUtils.hasText(text(binding.get("entrypoint")))) {
                report.error("definition.bindings[" + index + "].entrypoint is required for " + type);
            }
        }
        Object secretRefs = binding.get("secretRefs");
        if (secretRefs instanceof Collection<?> refs) {
            for (Object ref : refs) {
                if (!(ref instanceof String value) || !value.matches("^secret://[A-Za-z0-9._/-]+$")) {
                    report.error("definition.bindings[" + index + "].secretRefs must contain secret:// references");
                }
            }
        } else if (secretRefs != null) {
            report.error("definition.bindings[" + index + "].secretRefs must be an array");
        }
    }

    private void validatePortableHttpBinding(Map<String, Object> binding,
                                             int index,
                                             ValidationReportDTO report) {
        String endpoint = text(binding.get("endpointRef"));
        Map<String, Object> config = binding.get("config") instanceof Map<?, ?> raw ? cast(raw) : Map.of();
        URI uri = null;
        try {
            uri = URI.create(endpoint == null ? "" : endpoint);
            if (!uri.isAbsolute() || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException("absolute endpoint required");
            }
        } catch (IllegalArgumentException ex) {
            report.error("definition.bindings[" + index + "].endpointRef must be an absolute HTTP(S) URI");
        }
        String endpointHost = uri == null ? null : uri.getHost();
        Object allowedHosts = config.get("allowedHosts");
        if (!(allowedHosts instanceof Collection<?> hosts) || hosts.isEmpty()) {
            report.error("definition.bindings[" + index + "].config.allowedHosts is required");
        } else if (endpointHost != null
                && hosts.stream().noneMatch(host -> endpointHost.equalsIgnoreCase(String.valueOf(host)))) {
            report.error("definition.bindings[" + index + "].config.allowedHosts must include the endpoint host");
        }
        if (uri != null && "http".equalsIgnoreCase(uri.getScheme())
                && !Boolean.TRUE.equals(config.get("allowInsecureHttp"))) {
            report.error("definition.bindings[" + index + "] must set config.allowInsecureHttp=true for HTTP");
        }
        if (uri != null && !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
            report.error("definition.bindings[" + index + "].endpointRef must use HTTP or HTTPS");
        }
        if (config.get("headers") instanceof Map<?, ?> headers) {
            for (Object rawHeader : headers.keySet()) {
                String header = String.valueOf(rawHeader).toLowerCase(Locale.ROOT);
                if (Set.of("authorization", "proxy-authorization", "cookie", "set-cookie", "host")
                        .contains(header) || header.contains("secret") || header.contains("token")) {
                    report.error("definition.bindings[" + index + "].config.headers contains forbidden header: "
                            + rawHeader);
                }
            }
        }
        if (config.get("secretHeaders") instanceof Map<?, ?> secretHeaders) {
            secretHeaders.forEach((header, ref) -> {
                if (!(ref instanceof String value) || !value.matches("^secret://[A-Za-z0-9._/-]+$")) {
                    report.error("definition.bindings[" + index
                            + "].config.secretHeaders must contain secret:// references");
                }
            });
        }
    }

    private void rejectEmbeddedSecrets(Object value, String path, ValidationReportDTO report) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                Object child = entry.getValue();
                boolean secretReferenceMap = path.endsWith(".secretHeaders");
                if (SECRET_KEYS.contains(normalized) && hasValue(child) && !secretReferenceMap) {
                    report.error("Tool definition must not contain secret field: " + path + "." + key);
                }
                rejectEmbeddedSecrets(child, path + "." + key, report);
            }
        } else if (value instanceof Collection<?> values) {
            int index = 0;
            for (Object child : values) rejectEmbeddedSecrets(child, path + "[" + index++ + "]", report);
        }
    }

    private boolean hasValue(Object value) {
        if (value == null) return false;
        if (value instanceof String text) return StringUtils.hasText(text);
        if (value instanceof Collection<?> values) return !values.isEmpty();
        if (value instanceof Map<?, ?> values) return !values.isEmpty();
        return true;
    }

    private String text(Object value) {
        return value instanceof String string && StringUtils.hasText(string) ? string.trim() : null;
    }

    private Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Map<String, Object> cast(Map<?, ?> source) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
