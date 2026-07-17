package ai.platform.aiassit.agent.runtime.tool;

import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.agent.runtime.AgentCapabilityGrantService;
import ai.platform.aiassit.service.ai.spi.tool.PublishedToolDefinition;
import ai.platform.aiassit.service.ai.spi.tool.PublishedToolDefinitionStore;
import ai.platform.aiassit.service.ai.spi.tool.ToolApprovalVerifier;
import ai.platform.aiassit.service.ai.spi.tool.ToolInvocationPrincipal;
import ai.platform.aiassit.service.ai.spi.tool.ToolSecretResolver;
import ai.platform.aiassit.service.ai.spi.tool.ManagedToolExecutionRequest;
import ai.platform.aiassit.service.ai.spi.tool.ManagedToolExecutionResult;
import ai.platform.aiassit.service.ai.spi.tool.ManagedToolExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Final authorization and network boundary for portable versioned Tools.
 * Unsupported adapters and missing policy integrations fail closed.
 */
@Service
@Slf4j
public class ToolGatewayService {

    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> FORBIDDEN_STATIC_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "host");

    private final PublishedToolDefinitionStore definitionStore;
    private final List<ToolSecretResolver> secretResolvers;
    private final List<ToolApprovalVerifier> approvalVerifiers;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AgentCapabilityGrantService capabilityGrantService;
    private final ManagedToolExecutor managedToolExecutor;

    @Autowired
    public ToolGatewayService(PublishedToolDefinitionStore definitionStore,
                              List<ToolSecretResolver> secretResolvers,
                              List<ToolApprovalVerifier> approvalVerifiers,
                              ObjectMapper objectMapper,
                              AgentCapabilityGrantService capabilityGrantService,
                              ManagedToolExecutor managedToolExecutor) {
        this.definitionStore = definitionStore;
        this.secretResolvers = secretResolvers == null ? List.of() : List.copyOf(secretResolvers);
        this.approvalVerifiers = approvalVerifiers == null ? List.of() : List.copyOf(approvalVerifiers);
        this.objectMapper = objectMapper;
        this.capabilityGrantService = capabilityGrantService;
        this.managedToolExecutor = managedToolExecutor;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** Kept for focused HTTP gateway tests and embedders that do not enable managed code. */
    ToolGatewayService(PublishedToolDefinitionStore definitionStore,
                       List<ToolSecretResolver> secretResolvers,
                       List<ToolApprovalVerifier> approvalVerifiers,
                       ObjectMapper objectMapper,
                       AgentCapabilityGrantService capabilityGrantService) {
        this(definitionStore, secretResolvers, approvalVerifiers, objectMapper, capabilityGrantService, null);
    }

    public ToolGatewayResponse invoke(String toolCode,
                                      Integer toolVersion,
                                      ToolGatewayRequest request,
                                      ToolInvocationPrincipal principal,
                                      String approvalToken,
                                      String idempotencyKey) {
        long startedAt = System.currentTimeMillis();
        PublishedToolDefinition tool = definitionStore.findPublished(toolCode, toolVersion)
                .orElseThrow(() -> BizException.of(AiChatBizCodeConstant.TOOL_NOT_FOUND,
                        toolCode + "@" + toolVersion));
        if (principal == null || principal.getUserId() == null) {
            throw BizException.of(AiChatBizCodeConstant.TOOL_PERMISSION_DENIED, toolCode);
        }
        Map<String, Object> run = request == null ? Map.of() : safeMap(request.getRun());
        if (!capabilityGrantService.allows(
                text(run.get("runId")), principal.getUserId(), text(run.get("snapshotHash")),
                "tool", tool.getToolCode(), tool.getToolVersion())) {
            throw BizException.of(AiChatBizCodeConstant.TOOL_PERMISSION_DENIED,
                    "Tool is not granted to this Agent run");
        }
        Map<String, Object> definition = safeMap(tool.getDefinition());
        Map<String, Object> arguments = request == null ? Map.of() : safeMap(request.getArguments());
        validateSchema(mapValue(definition.get("inputSchema")), arguments, "$", true);
        enforcePermissions(tool, definition, principal);
        enforceApproval(tool, definition, principal, approvalToken, arguments);

        if ("MANAGED_CODE".equalsIgnoreCase(text(definition.get("executionMode")))) {
            if (managedToolExecutor == null) {
                throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED,
                        "Managed Tool runtime is not available");
            }
            Map<String, Object> context = new LinkedHashMap<>(run);
            context.put("toolCode", tool.getToolCode());
            context.put("toolVersion", tool.getToolVersion());
            context.put("userId", principal.getUserId());
            context.put("traceId", principal.getTraceId());
            context.put("config", mapValue(definition.get("runtimeConfig")));
            ManagedToolExecutionResult execution;
            try {
                execution = managedToolExecutor.execute(ManagedToolExecutionRequest.builder()
                        .definition(definition)
                        .arguments(arguments)
                        .context(context)
                        .executionToken(principal.getExecutionToken())
                        .build());
            } catch (Exception ex) {
                log.warn("managed Tool invocation failed: toolCode={}, version={}, userId={}, errorType={}",
                        tool.getToolCode(), tool.getToolVersion(), principal.getUserId(), ex.getClass().getSimpleName());
                throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED, safeMessage(ex));
            }
            validateSchema(mapValue(definition.get("outputSchema")), execution.getOutput(), "$", false);
            long duration = System.currentTimeMillis() - startedAt;
            log.info("managed Tool invocation completed: toolCode={}, version={}, userId={}, runtime={}, durationMs={}, result=success",
                    tool.getToolCode(), tool.getToolVersion(), principal.getUserId(),
                    text(definition.get("implementationRuntime")), duration);
            return ToolGatewayResponse.builder()
                    .toolCode(tool.getToolCode())
                    .toolVersion(tool.getToolVersion())
                    .status("SUCCESS")
                    .output(execution.getOutput())
                    .durationMs(duration)
                    .build();
        }

        String adapter = text(tool.getAdapterType());
        if (!"HTTP".equals(adapter) && !"FUNCTION".equals(adapter)) {
            throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED,
                    "Tool adapter is not available through the portable gateway: " + adapter);
        }
        URI endpoint = endpoint(definition);
        String method = text(definition.get("method"));
        method = StringUtils.hasText(method) ? method.toUpperCase(Locale.ROOT) : "POST";
        if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
            throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED, "Unsupported HTTP method");
        }
        if (MUTATING_METHODS.contains(method) && !StringUtils.hasText(idempotencyKey)) {
            throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED,
                    "Idempotency-Key is required for mutating Tools");
        }
        enforceNetworkPolicy(endpoint, definition);
        Object output = executeHttp(tool, endpoint, method, definition, arguments, principal, idempotencyKey);
        validateSchema(mapValue(definition.get("outputSchema")), output, "$", false);
        long duration = System.currentTimeMillis() - startedAt;
        log.info("tool gateway invocation completed: toolCode={}, version={}, userId={}, method={}, host={}, durationMs={}, result=success",
                tool.getToolCode(), tool.getToolVersion(), principal.getUserId(), method, endpoint.getHost(), duration);
        return ToolGatewayResponse.builder()
                .toolCode(tool.getToolCode())
                .toolVersion(tool.getToolVersion())
                .status("SUCCESS")
                .output(output)
                .durationMs(duration)
                .build();
    }

    private Object executeHttp(PublishedToolDefinition tool,
                               URI endpoint,
                               String method,
                               Map<String, Object> definition,
                               Map<String, Object> arguments,
                               ToolInvocationPrincipal principal,
                               String idempotencyKey) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(arguments);
            if (payload.length > MAX_BODY_BYTES) {
                throw BizException.of(AiChatBizCodeConstant.INVALID_TOOL_INPUT, "Tool input is too large");
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(timeoutMs(definition)))
                    .header("Accept", "application/json")
                    .header("X-AI-Tool-Code", tool.getToolCode())
                    .header("X-AI-Tool-Version", String.valueOf(tool.getToolVersion()))
                    .header("X-AI-User-Id", String.valueOf(principal.getUserId()));
            if (StringUtils.hasText(principal.getTraceId())) {
                builder.header("X-Trace-Id", principal.getTraceId());
            }
            if (StringUtils.hasText(idempotencyKey)) {
                builder.header("Idempotency-Key", idempotencyKey.trim());
            }
            staticHeaders(definition).forEach(builder::header);
            secretHeaders(definition, principal).forEach(builder::header);
            if ("GET".equals(method)) {
                if (!arguments.isEmpty()) {
                    throw BizException.of(AiChatBizCodeConstant.INVALID_TOOL_INPUT,
                            "GET Tools do not accept a request body");
                }
                builder.GET();
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofByteArray(payload));
            }
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.body().length > MAX_BODY_BYTES) {
                throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED, "Tool output is too large");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED,
                        "Tool endpoint returned HTTP " + response.statusCode());
            }
            if (response.body().length == 0) {
                return Map.of();
            }
            try {
                return objectMapper.readValue(response.body(), Object.class);
            } catch (JsonProcessingException ex) {
                return new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (BizException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED, "Tool invocation interrupted");
        } catch (Exception ex) {
            log.warn("tool gateway invocation failed: toolCode={}, version={}, userId={}, errorType={}",
                    tool.getToolCode(), tool.getToolVersion(), principal.getUserId(), ex.getClass().getSimpleName());
            throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED, safeMessage(ex));
        }
    }

    private void enforcePermissions(PublishedToolDefinition tool,
                                    Map<String, Object> definition,
                                    ToolInvocationPrincipal principal) {
        Map<String, Object> policy = mapValue(definition.get("permissionPolicy"));
        List<String> requiredPermissions = stringList(policy.get("requiredPermissions"));
        List<String> requiredRoles = stringList(policy.get("requiredRoles"));
        boolean permissionsAllowed = principal.getPermissions().containsAll(requiredPermissions);
        boolean rolesAllowed = requiredRoles.isEmpty() || requiredRoles.stream()
                .anyMatch(required -> containsIgnoreCase(principal.getRoles(), required));
        if (!permissionsAllowed || !rolesAllowed) {
            log.warn("tool gateway permission denied: toolCode={}, version={}, userId={}",
                    tool.getToolCode(), tool.getToolVersion(), principal.getUserId());
            throw BizException.of(AiChatBizCodeConstant.TOOL_PERMISSION_DENIED, tool.getToolCode());
        }
    }

    private void enforceApproval(PublishedToolDefinition tool,
                                 Map<String, Object> definition,
                                 ToolInvocationPrincipal principal,
                                 String approvalToken,
                                 Map<String, Object> arguments) {
        Map<String, Object> policy = mapValue(definition.get("approvalPolicy"));
        if (!Boolean.TRUE.equals(policy.get("required"))) {
            return;
        }
        boolean approved = StringUtils.hasText(approvalToken) && approvalVerifiers.stream()
                .anyMatch(verifier -> verifier.verify(tool, principal, approvalToken.trim(), arguments));
        if (!approved) {
            throw BizException.of(AiChatBizCodeConstant.TOOL_APPROVAL_REQUIRED, tool.getToolCode());
        }
    }

    private URI endpoint(Map<String, Object> definition) {
        String value = text(definition.get("endpoint"));
        if (!StringUtils.hasText(value)) {
            value = text(definition.get("handlerRef"));
        }
        try {
            URI uri = URI.create(value == null ? "" : value);
            if (!uri.isAbsolute() || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException("absolute endpoint required");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED,
                    "Tool endpoint is invalid");
        }
    }

    private void enforceNetworkPolicy(URI endpoint, Map<String, Object> definition) {
        String scheme = endpoint.getScheme() == null ? "" : endpoint.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !("http".equals(scheme)
                && Boolean.TRUE.equals(definition.get("allowInsecureHttp")))) {
            throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED,
                    "Tool endpoint must use HTTPS");
        }
        List<String> allowlist = stringList(definition.get("allowedHosts"));
        if (allowlist.isEmpty() || allowlist.stream().noneMatch(host -> host.equalsIgnoreCase(endpoint.getHost()))) {
            throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED,
                    "Tool endpoint host is not allowlisted");
        }
    }

    private Map<String, String> staticHeaders(Map<String, Object> definition) {
        Map<String, String> result = new LinkedHashMap<>();
        mapValue(definition.get("headers")).forEach((key, value) -> {
            String normalized = key.toLowerCase(Locale.ROOT);
            if (FORBIDDEN_STATIC_HEADERS.contains(normalized) || value == null) {
                throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED,
                        "Forbidden static Tool header: " + key);
            }
            result.put(key, String.valueOf(value));
        });
        return result;
    }

    private Map<String, String> secretHeaders(Map<String, Object> definition,
                                               ToolInvocationPrincipal principal) {
        Map<String, String> result = new LinkedHashMap<>();
        mapValue(definition.get("secretHeaders")).forEach((header, referenceValue) -> {
            String reference = text(referenceValue);
            if (!StringUtils.hasText(reference)) {
                throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED,
                        "Secret reference is required for " + header);
            }
            ToolSecretResolver resolver = secretResolvers.stream()
                    .filter(candidate -> candidate.supports(reference))
                    .findFirst()
                    .orElseThrow(() -> BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED,
                            "No resolver is configured for Tool secret reference"));
            result.put(header, resolver.resolve(reference, principal));
        });
        return result;
    }

    private void validateSchema(Map<String, Object> schema, Object value, String path, boolean requireObject) {
        if (schema.isEmpty()) {
            if (requireObject && !(value instanceof Map<?, ?>)) {
                throw BizException.of(AiChatBizCodeConstant.INVALID_TOOL_INPUT, path + " must be an object");
            }
            return;
        }
        String type = text(schema.get("type"));
        if ("object".equals(type) || (type == null && schema.get("properties") instanceof Map<?, ?>)) {
            if (!(value instanceof Map<?, ?> raw)) {
                invalidSchemaValue(path + " must be an object");
            }
            Map<String, Object> object = safeMap((Map<?, ?>) value);
            for (String required : stringList(schema.get("required"))) {
                if (!object.containsKey(required) || object.get(required) == null) {
                    invalidSchemaValue(path + "." + required + " is required");
                }
            }
            Map<String, Object> properties = mapValue(schema.get("properties"));
            if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
                for (String key : object.keySet()) {
                    if (!properties.containsKey(key)) invalidSchemaValue(path + "." + key + " is not allowed");
                }
            }
            object.forEach((key, item) -> {
                Map<String, Object> property = mapValue(properties.get(key));
                if (!property.isEmpty() && item != null) validateSchema(property, item, path + "." + key, false);
            });
        } else if ("array".equals(type)) {
            if (!(value instanceof Collection<?> values)) invalidSchemaValue(path + " must be an array");
            Map<String, Object> items = mapValue(schema.get("items"));
            int index = 0;
            for (Object item : (Collection<?>) value) validateSchema(items, item, path + "[" + index++ + "]", false);
        } else if ("string".equals(type) && !(value instanceof String)) {
            invalidSchemaValue(path + " must be a string");
        } else if ("integer".equals(type) && !(value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long || value instanceof java.math.BigInteger)) {
            invalidSchemaValue(path + " must be an integer");
        } else if ("number".equals(type) && !(value instanceof Number)) {
            invalidSchemaValue(path + " must be a number");
        } else if ("boolean".equals(type) && !(value instanceof Boolean)) {
            invalidSchemaValue(path + " must be a boolean");
        }
        List<Object> allowed = objectList(schema.get("enum"));
        if (!allowed.isEmpty() && !allowed.contains(value)) invalidSchemaValue(path + " is not an allowed value");
    }

    private void invalidSchemaValue(String message) {
        throw BizException.of(AiChatBizCodeConstant.INVALID_TOOL_INPUT, message);
    }

    private int timeoutMs(Map<String, Object> definition) {
        Object value = definition.get("timeoutMs");
        int timeout = value instanceof Number number ? number.intValue() : 30_000;
        return Math.max(100, Math.min(timeout, 300_000));
    }

    private String safeMessage(Exception ex) {
        String value = ex.getMessage();
        if (!StringUtils.hasText(value)) return ex.getClass().getSimpleName();
        String sanitized = value.replaceAll("(?i)bearer\\s+[^\\s,;]+", "Bearer [REDACTED]")
                .replaceAll("\\bsk-[A-Za-z0-9_-]{8,}\\b", "[REDACTED]");
        return sanitized.substring(0, Math.min(sanitized.length(), 512));
    }

    private boolean containsIgnoreCase(Set<String> values, String expected) {
        return values != null && values.stream().anyMatch(value -> value != null && value.equalsIgnoreCase(expected));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? safeMap(map) : Map.of();
    }

    private Map<String, Object> safeMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value != null) value.forEach((key, item) -> { if (key != null) result.put(String.valueOf(key), item); });
        return result;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : values) if (StringUtils.hasText(text(item))) result.add(text(item));
        return result;
    }

    private List<Object> objectList(Object value) {
        return value instanceof Collection<?> values ? new ArrayList<>(values) : List.of();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
