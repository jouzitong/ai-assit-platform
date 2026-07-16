package ai.platform.aiassit.conversation.workflow.artifact;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fail-closed deterministic Artifact acceptance. */
@Service
public class DefaultArtifactAcceptanceService implements ArtifactAcceptanceService {

    private final ObjectMapper objectMapper;

    public DefaultArtifactAcceptanceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ArtifactAcceptanceResult accept(Map<String, Object> workflowSnapshot,
                                           List<Map<String, Object>> artifacts,
                                           String finalAnswer) {
        Map<String, Object> spec = spec(workflowSnapshot);
        List<Map<String, Object>> contracts = maps(spec.get("artifacts"));
        List<Map<String, Object>> normalized = normalizeArtifacts(artifacts, finalAnswer);
        Map<String, Map<String, Object>> byCode = index(normalized);
        List<ArtifactCheckResult> checks = new ArrayList<>();

        if (contracts.isEmpty()) {
            contracts = List.of(defaultFinalAnswerContract());
        }
        Map<String, Map<String, Object>> contractsByCode = index(contracts);
        Map<String, Object> completionPolicy = map(spec.get("completionPolicy"));
        boolean requireAllRequiredArtifacts = !Boolean.FALSE.equals(
                completionPolicy.get("requireAllRequiredArtifacts"));
        boolean requireAllBlockingChecksPassed = !Boolean.FALSE.equals(
                completionPolicy.get("requireAllBlockingChecksPassed"));
        for (Map<String, Object> contract : contracts) {
            String code = text(contract.get("code"), contract.get("artifactCode"));
            boolean required = !Boolean.FALSE.equals(contract.get("required"));
            Map<String, Object> artifact = byCode.get(code);
            if (requireAllRequiredArtifacts && required && artifact == null) {
                checks.add(result("required-" + code, code, "REQUIRED", "ERROR",
                        true, true, false, "FAILED", "Required artifact is missing: " + code));
                continue;
            }
            if (artifact != null) {
                Map<String, Object> schema = map(contract.get("inlineSchema"));
                if (schema.isEmpty()) {
                    schema = map(contract.get("schema"));
                }
                if (!schema.isEmpty()) {
                    checks.add(validateSchema("schema-" + code, code, artifact.get("content"), schema, true, true));
                } else if (StringUtils.hasText(text(contract.get("schemaRef")))) {
                    checks.add(result("schema-" + code, code, "JSON_SCHEMA", "ERROR",
                            true, true, false, "FAILED",
                            "Published schemaRef was not resolved into the Workflow snapshot: "
                                    + contract.get("schemaRef")));
                }
            }
        }

        for (Map<String, Object> check : maps(spec.get("checks"))) {
            String checkerType = text(check.get("checkerType"), "JSON_SCHEMA").toUpperCase();
            String code = text(check.get("code"), check.get("checkCode"), "check-" + (checks.size() + 1));
            String target = text(check.get("targetArtifact"), check.get("artifactCode"));
            boolean blocking = !Boolean.FALSE.equals(check.get("blocking"));
            boolean retryable = Boolean.TRUE.equals(check.get("retryable"));
            String severity = text(check.get("severity"), blocking ? "ERROR" : "WARNING");
            Map<String, Object> artifact = byCode.get(target);
            Map<String, Object> contract = contractsByCode.get(target);
            if (artifact == null && contract != null && Boolean.FALSE.equals(contract.get("required"))) {
                checks.add(result(code, target, checkerType, severity, blocking, retryable,
                        true, "SKIPPED", "Optional target artifact was not produced"));
                continue;
            }
            if ("JSON_SCHEMA".equals(checkerType)) {
                Map<String, Object> schema = map(check.get("schema"));
                if (schema.isEmpty()) {
                    schema = map(map(check.get("config")).get("schema"));
                }
                if (schema.isEmpty() && contract != null) {
                    schema = map(contract.get("inlineSchema"));
                    if (schema.isEmpty()) {
                        schema = map(contract.get("schema"));
                    }
                }
                checks.add(validateSchema(code, target, artifact == null ? null : artifact.get("content"),
                        schema, blocking, retryable));
            } else {
                boolean passed = !blocking;
                checks.add(result(code, target, checkerType, severity, blocking, retryable, passed,
                        passed ? "SKIPPED" : "FAILED",
                        passed
                                ? checkerType + " check is delegated and non-blocking"
                                : "Unsupported blocking checker must not fail open: " + checkerType));
            }
        }

        boolean requiredFailure = checks.stream().anyMatch(check -> "REQUIRED".equals(check.getCheckerType())
                && !check.isPassed());
        boolean blockingCheckFailure = checks.stream().anyMatch(check -> !"REQUIRED".equals(check.getCheckerType())
                && check.isBlocking() && !check.isPassed());
        boolean accepted = !requiredFailure && (!requireAllBlockingChecksPassed || !blockingCheckFailure);
        boolean repairable = !accepted && checks.stream().anyMatch(check -> !check.isPassed() && check.isRetryable());
        Map<String, Object> repairPolicy = map(spec.get("repairPolicy"));
        int maxAttempts = intValue(repairPolicy.get("maxRepairAttempts"), 0);
        String onExhausted = text(repairPolicy.get("onExhausted"), "FAILED").toUpperCase();

        ArtifactAcceptanceResult result = new ArtifactAcceptanceResult();
        result.setAccepted(accepted);
        result.setRepairable(repairable && maxAttempts > 0);
        result.setInputRequired(!accepted && "INPUT_REQUIRED".equals(onExhausted));
        result.setMaxRepairAttempts(maxAttempts);
        result.setOnExhausted(onExhausted);
        result.setArtifacts(normalized);
        result.setChecks(checks);
        result.setRepairMessage(repairMessage(checks));
        return result;
    }

    private List<Map<String, Object>> normalizeArtifacts(List<Map<String, Object>> source, String finalAnswer) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (source != null) {
            for (Map<String, Object> artifact : source) {
                if (artifact == null) {
                    continue;
                }
                Map<String, Object> copy = new LinkedHashMap<>(artifact);
                String code = text(copy.get("code"), copy.get("artifactCode"));
                if (StringUtils.hasText(code)) {
                    copy.put("artifactCode", code);
                    result.add(copy);
                }
            }
        }
        boolean hasFinal = result.stream().anyMatch(item -> "final-answer".equals(item.get("artifactCode")));
        if (!hasFinal && StringUtils.hasText(finalAnswer)) {
            Map<String, Object> finalArtifact = new LinkedHashMap<>();
            finalArtifact.put("artifactCode", "final-answer");
            finalArtifact.put("artifactType", "TEXT");
            finalArtifact.put("contentFormat", "MARKDOWN");
            finalArtifact.put("required", true);
            // The assistant message is the user-facing final answer. Keep this synthetic Artifact
            // for deterministic Workflow acceptance, but do not render the same content twice.
            finalArtifact.put("visible", false);
            finalArtifact.put("content", finalAnswer);
            result.add(finalArtifact);
        }
        return result;
    }

    private ArtifactCheckResult validateSchema(String code,
                                               String target,
                                               Object content,
                                               Map<String, Object> schema,
                                               boolean blocking,
                                               boolean retryable) {
        if (schema == null || schema.isEmpty()) {
            return result(code, target, "JSON_SCHEMA", blocking ? "ERROR" : "WARNING",
                    blocking, retryable, !blocking, blocking ? "FAILED" : "SKIPPED",
                    blocking ? "Blocking JSON Schema check has no schema" : "JSON Schema is not configured");
        }
        JsonNode value;
        try {
            value = content instanceof String text && looksJson(text)
                    ? objectMapper.readTree(text)
                    : objectMapper.valueToTree(content);
        } catch (JsonProcessingException ex) {
            return result(code, target, "JSON_SCHEMA", "ERROR", blocking, retryable,
                    false, "FAILED", "Artifact is not valid JSON");
        }
        List<String> errors = new ArrayList<>();
        validateNode(value, schema, "$", errors);
        return result(code, target, "JSON_SCHEMA", blocking ? "ERROR" : "WARNING",
                blocking, retryable, errors.isEmpty(), errors.isEmpty() ? "PASSED" : "FAILED",
                errors.isEmpty() ? "JSON Schema check passed" : String.join("; ", errors));
    }

    private void validateNode(JsonNode value, Map<String, Object> schema, String path, List<String> errors) {
        String type = text(schema.get("type"));
        if (StringUtils.hasText(type) && !matchesType(value, type)) {
            errors.add(path + " must be " + type);
            return;
        }
        Collection<?> enumValues = schema.get("enum") instanceof Collection<?> collection ? collection : List.of();
        if (!enumValues.isEmpty() && enumValues.stream().noneMatch(item -> String.valueOf(item).equals(value.asText()))) {
            errors.add(path + " is not an allowed enum value");
        }
        if (value != null && value.isObject()) {
            Set<String> required = new LinkedHashSet<>();
            if (schema.get("required") instanceof Collection<?> values) {
                values.forEach(item -> required.add(String.valueOf(item)));
            }
            for (String key : required) {
                if (!value.has(key) || value.get(key).isNull()) {
                    errors.add(path + "." + key + " is required");
                }
            }
            Map<String, Object> properties = map(schema.get("properties"));
            properties.forEach((key, childSchema) -> {
                if (value.has(key) && childSchema instanceof Map<?, ?> child) {
                    validateNode(value.get(key), cast(child), path + "." + key, errors);
                }
            });
        }
        if (value != null && value.isArray() && schema.get("items") instanceof Map<?, ?> items) {
            for (int i = 0; i < value.size(); i++) {
                validateNode(value.get(i), cast(items), path + "[" + i + "]", errors);
            }
        }
    }

    private boolean matchesType(JsonNode value, String type) {
        if (value == null || value.isNull()) return "null".equals(type);
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private Map<String, Object> spec(Map<String, Object> workflow) {
        Map<String, Object> source = workflow == null ? Map.of() : workflow;
        Map<String, Object> specification = map(source.get("specification"));
        if (!specification.isEmpty()) {
            source = specification;
        }
        Map<String, Object> nested = map(source.get("spec"));
        return nested.isEmpty() ? source : nested;
    }

    private Map<String, Map<String, Object>> index(List<Map<String, Object>> artifacts) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> artifact : artifacts) {
            String code = text(artifact.get("artifactCode"), artifact.get("code"));
            if (StringUtils.hasText(code)) {
                result.put(code, artifact);
            }
        }
        return result;
    }

    private Map<String, Object> defaultFinalAnswerContract() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", "final-answer");
        result.put("artifactType", "TEXT");
        result.put("required", true);
        return result;
    }

    private String repairMessage(List<ArtifactCheckResult> checks) {
        List<String> failures = checks.stream()
                .filter(check -> !check.isPassed())
                .map(check -> check.getCheckCode() + ": " + check.getMessage())
                .toList();
        return failures.isEmpty() ? null
                : "Revise the artifact set and fix every failed check:\n- " + String.join("\n- ", failures);
    }

    private ArtifactCheckResult result(String code, String target, String type, String severity,
                                       boolean blocking, boolean retryable, boolean passed,
                                       String status, String message) {
        return ArtifactCheckResult.builder()
                .checkCode(code)
                .targetArtifact(target)
                .checkerType(type)
                .severity(severity)
                .blocking(blocking)
                .retryable(retryable)
                .passed(passed)
                .status(status)
                .message(message)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? cast(map) : Map.of();
    }

    private Map<String, Object> cast(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                result.add(cast(map));
            }
        }
        return result;
    }

    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean looksJson(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private String text(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }
}
