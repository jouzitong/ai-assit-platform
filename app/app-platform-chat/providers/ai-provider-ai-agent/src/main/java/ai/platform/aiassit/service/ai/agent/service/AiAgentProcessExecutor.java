package ai.platform.aiassit.service.ai.agent.service;

import ai.platform.aiassit.service.ai.agent.config.AiAgentProperties;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.ChatMessage;
import ai.platform.aiassit.service.ai.spi.agent.AgentCancellation;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import ai.platform.aiassit.service.ai.spi.agent.AgentModelConnection;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunCommand;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AiAgentProcessExecutor {

    private static final String PROTOCOL_VERSION = "2.0";
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)bearer\\s+[^\\s,;]+");
    private static final Pattern OPENAI_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_-]{8,}\\b");
    private static final Set<String> RUNTIME_EXTENSION_KEYS = Set.of(
            "protocolVersion",
            "run",
            "rootAgent",
            "agentGraph",
            "resolvedCapabilities",
            "workflowSnapshot",
            "snapshotHash",
            "modelSettings"
    );
    private static final Set<String> MODEL_SETTING_KEYS = Set.of(
            "temperature", "topP", "top_p", "maxTokens", "max_tokens",
            "frequencyPenalty", "frequency_penalty", "presencePenalty", "presence_penalty",
            "parallelToolCalls", "parallel_tool_calls", "toolChoice", "tool_choice",
            "topLogprobs", "top_logprobs", "verbosity", "reasoning",
            "promptCacheRetention", "prompt_cache_retention", "includeUsage", "include_usage"
    );
    private static final Set<String> SENSITIVE_EXTENSION_KEYS = Set.of(
            "apikey", "token", "accesstoken", "refreshtoken", "password", "secret",
            "clientsecret", "authorization", "credential", "cookie"
    );

    private static final Path DEPLOYED_SCRIPT_PATH = Path.of(
            "python",
            "agent_provider",
            "main.py"
    );

    private static final Path DEV_SCRIPT_PATH = Path.of(
            "app",
            "app-platform-chat",
            "providers",
            "ai-provider-ai-agent",
            "src",
            "main",
            "python",
            "agent_provider",
            "main.py"
    );

    private final ObjectMapper objectMapper;

    public AiAgentProcessExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode execute(AiAgentProperties properties, ProviderChatRequest request) {
        return executeStream(properties, request, frame -> { });
    }

    public JsonNode executeStream(AiAgentProperties properties,
                                  ProviderChatRequest request,
                                  Consumer<JsonNode> frameConsumer) {
        return executeStream(
                properties,
                request,
                frameConsumer,
                AgentCancellation.NONE,
                properties.getPythonCommand(),
                resolveScriptPath(properties),
                properties.getWorkingDirectory(),
                Map.of()
        );
    }

    public JsonNode executeAgent(AiAgentProperties properties,
                                 AgentDefinitionSnapshot snapshot,
                                 AgentRunCommand command,
                                 Consumer<JsonNode> frameConsumer,
                                 AgentCancellation cancellation) {
        if (snapshot == null) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "agent snapshot is required");
        }
        if (command == null) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "agent run command is required");
        }
        ProviderChatRequest request = toProviderRequest(snapshot, command);
        return executeStream(
                properties,
                request,
                frameConsumer == null ? frame -> { } : frameConsumer,
                cancellation == null ? AgentCancellation.NONE : cancellation,
                properties.getPythonCommand(),
                resolveScriptPath(properties),
                properties.getWorkingDirectory(),
                Map.of()
        );
    }

    JsonNode executeAgentWithWorker(AiAgentProperties properties,
                                    AgentDefinitionSnapshot snapshot,
                                    AgentRunCommand command,
                                    Consumer<JsonNode> frameConsumer,
                                    AgentCancellation cancellation,
                                    String workerCommand,
                                    Path scriptPath,
                                    String workingDirectory,
                                    Map<String, String> additionalEnvironment) {
        if (snapshot == null) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "agent snapshot is required");
        }
        if (command == null) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "agent run command is required");
        }
        return executeStream(
                properties,
                toProviderRequest(snapshot, command),
                frameConsumer == null ? frame -> { } : frameConsumer,
                cancellation == null ? AgentCancellation.NONE : cancellation,
                workerCommand,
                scriptPath,
                workingDirectory,
                additionalEnvironment == null ? Map.of() : additionalEnvironment
        );
    }

    private JsonNode executeStream(AiAgentProperties properties,
                                   ProviderChatRequest request,
                                   Consumer<JsonNode> frameConsumer,
                                   AgentCancellation cancellation,
                                   String workerCommand,
                                   Path scriptPath,
                                   String workingDirectory,
                                   Map<String, String> additionalEnvironment) {
        if (request == null) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "provider request is required");
        }
        if (cancellation.isCancellationRequested()) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, "agent run cancelled");
        }
        String apiKey = resolveValue(request == null ? null : request.getApiKey(), properties.getApiKey());
        String baseUrl = resolveValue(request == null ? null : request.getBaseUrl(), properties.getBaseUrl());
        validate(apiKey, workerCommand);
        long timeoutMs = resolveTimeoutMs(properties, request);
        log.info("ai agent process preparing, scriptPath={}, workerCommand={}, model={}, timeoutMs={}, workingDirectory={}",
                scriptPath, workerCommand,
                StringUtils.hasText(request.getModel()) ? request.getModel() : properties.getDefaultModel(),
                timeoutMs, workingDirectory);
        List<String> command = new ArrayList<>();
        command.add(workerCommand);
        command.add(scriptPath.toString());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (StringUtils.hasText(workingDirectory)) {
            processBuilder.directory(Path.of(workingDirectory).toFile());
        }
        Map<String, String> env = processBuilder.environment();
        env.putAll(additionalEnvironment);
        env.put("OPENAI_API_KEY", apiKey);
        if (StringUtils.hasText(baseUrl)) {
            env.put("OPENAI_BASE_URL", baseUrl);
        }
        if (StringUtils.hasText(request.getModel())) {
            env.put("OPENAI_MODEL", request.getModel());
        } else if (StringUtils.hasText(properties.getDefaultModel())) {
            env.put("OPENAI_MODEL", properties.getDefaultModel());
        }
        if (StringUtils.hasText(properties.getKnowledgeSearchUrl())) {
            env.put("AI_AGENT_KB_SEARCH_URL", properties.getKnowledgeSearchUrl());
        }
        if (StringUtils.hasText(properties.getToolGatewayUrl())) {
            env.put("AI_AGENT_TOOL_GATEWAY_URL", properties.getToolGatewayUrl());
        }
        if (StringUtils.hasText(properties.getSkillGatewayUrl())) {
            env.put("AI_AGENT_SKILL_GATEWAY_URL", properties.getSkillGatewayUrl());
        }
        if (StringUtils.hasText(properties.getValidateContentType())) {
            env.put("AI_AGENT_VALIDATE_CONTENT_TYPE", properties.getValidateContentType());
        }
        if (StringUtils.hasText(properties.getValidateStructure())) {
            env.put("AI_AGENT_VALIDATE_STRUCTURE", properties.getValidateStructure());
        }
        Object currentUser = SystemContext.getUserContext();
        if (currentUser instanceof UserContext userContext && StringUtils.hasText(userContext.token())) {
            env.put("AI_AGENT_KB_SEARCH_TOKEN", userContext.token());
            env.put("AI_AGENT_TOOL_GATEWAY_TOKEN", userContext.token());
            env.put("AI_AGENT_SKILL_GATEWAY_TOKEN", userContext.token());
        }

        Process process = null;
        try {
            log.info("ai agent process starting, command={}", command);
            process = processBuilder.start();
            Process activeProcess = process;
            log.info("ai agent process started");
            try (Writer writer = new OutputStreamWriter(activeProcess.getOutputStream(), StandardCharsets.UTF_8)) {
                objectMapper.writeValue(writer, buildPayload(properties, request));
            }
            AtomicReference<JsonNode> resultRef = new AtomicReference<>();
            CompletableFuture<Void> stdoutFuture = CompletableFuture.runAsync(
                    () -> readFrames(activeProcess, frameConsumer, resultRef));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                    () -> readText(activeProcess.getErrorStream()));
            boolean finished = waitFor(activeProcess, timeoutMs, cancellation, stdoutFuture);
            if (!finished) {
                log.warn("ai agent process timeout, timeoutMs={}, scriptPath={}", timeoutMs, scriptPath);
                activeProcess.destroyForcibly();
                throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, "agent worker process timeout");
            }
            stdoutFuture.join();
            String stderr = stderrFuture.join();
            int exitValue = activeProcess.exitValue();
            log.info("ai agent process finished, exitValue={}, stderrLength={}", exitValue, stderr.length());
            if (exitValue != 0) {
                log.error("ai agent process failed, exitValue={}, stderr={}", exitValue, safeError(stderr, apiKey));
                throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, safeError(stderr, apiKey));
            }
            JsonNode result = resultRef.get();
            if (result == null) {
                log.error("ai agent process returned empty output, stderr={}", safeError(stderr, apiKey));
                throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "empty output");
            }
            log.info("ai agent process parsed output successfully");
            return result;
        } catch (InterruptedException ex) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, "agent worker process interrupted");
        } catch (Exception ex) {
            Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof BizException bizException) {
                throw bizException;
            }
            String message = safeError(cause.getMessage(), apiKey);
            log.error("ai agent provider execute failed, scriptPath={}, model={}, error={}",
                    scriptPath,
                    StringUtils.hasText(request.getModel()) ? request.getModel() : properties.getDefaultModel(),
                    message);
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, message);
        }
    }

    private void readFrames(Process process,
                            Consumer<JsonNode> frameConsumer,
                            AtomicReference<JsonNode> resultRef) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                JsonNode frame = objectMapper.readTree(line);
                String type = frame.path("type").asText();
                if (!StringUtils.hasText(type)) {
                    resultRef.set(frame);
                } else if ("result".equalsIgnoreCase(type)) {
                    resultRef.set(frame.get("data"));
                } else if ("error".equalsIgnoreCase(type)) {
                    frameConsumer.accept(frame);
                    throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED,
                            frame.path("message").asText("ai agent execution failed"));
                } else {
                    frameConsumer.accept(frame);
                }
            }
        } catch (Exception ex) {
            if (ex instanceof BizException bizException) {
                throw bizException;
            }
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, ex.getMessage());
        }
    }

    private String readText(java.io.InputStream inputStream) {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
            return builder.toString();
        } catch (Exception ex) {
            return ex.getMessage() == null ? "" : ex.getMessage();
        }
    }

    Map<String, Object> buildPayload(AiAgentProperties properties, ProviderChatRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> runtime = runtimeExtension(request.getExt());
        payload.put("protocolVersion", runtime.getOrDefault("protocolVersion", PROTOCOL_VERSION));
        payload.put("model", StringUtils.hasText(request.getModel()) ? request.getModel() : properties.getDefaultModel());
        payload.put("messages", normalizeMessages(request.getMessages()));
        payload.put("tools", sanitizeStructuredValue(request.getTools()));
        payload.put("responseFormat", request.getResponseFormat());
        payload.put("meta", sanitizeStructuredValue(request.getMeta()));
        payload.put("ext", legacyExtensions(request.getExt()));
        payload.put("run", sanitizeStructuredValue(runtime.get("run")));
        payload.put("rootAgent", runtime.get("rootAgent"));
        payload.put("agentGraph", runtime.get("agentGraph"));
        payload.put("resolvedCapabilities", runtime.get("resolvedCapabilities"));
        payload.put("workflowSnapshot", runtime.get("workflowSnapshot"));
        payload.put("snapshotHash", runtime.get("snapshotHash"));
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", request.getTemperature());
        options.put("topP", request.getTopP());
        options.put("maxTokens", request.getMaxTokens());
        options.put("timeoutMs", resolveTimeoutMs(properties, request));
        Object modelSettings = runtime.get("modelSettings");
        if (modelSettings instanceof Map<?, ?> settings) {
            settings.forEach((key, value) -> {
                if (key instanceof String stringKey && MODEL_SETTING_KEYS.contains(stringKey) && value != null) {
                    options.put(stringKey, value);
                }
            });
        }
        payload.put("options", options);
        return payload;
    }

    private List<Map<String, Object>> normalizeMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message == null || message.getRole() == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.getRole().name().toLowerCase(Locale.ROOT));
            item.put("content", message.getContent());
            if (StringUtils.hasText(message.getName())) {
                item.put("name", message.getName());
            }
            normalized.add(item);
        }
        return normalized;
    }

    private ProviderChatRequest toProviderRequest(AgentDefinitionSnapshot snapshot, AgentRunCommand command) {
        ProviderChatRequest request = new ProviderChatRequest();
        AgentModelConnection connection = command.getModelConnection();
        if (connection != null) {
            request.setBaseUrl(connection.getBaseUrl());
            request.setApiKey(connection.getApiKey());
            request.setModel(connection.getModel());
            Map<String, Object> settings = connection.getSettings();
            if (settings != null) {
                request.setTemperature(doubleValue(settings.get("temperature")));
                request.setTopP(doubleValue(settings.get("topP"), settings.get("top_p")));
                request.setMaxTokens(integerValue(settings.get("maxTokens"), settings.get("max_tokens")));
            }
        }
        request.setMessages(command.getMessages());
        request.setTimeoutMs(command.getTimeoutMs());

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("runId", command.getRunId());
        run.put("requestId", command.getRequestId());
        run.put("traceId", command.getTraceId());
        run.put("sessionCode", command.getSessionCode());
        run.put("roundCode", command.getRoundCode());
        run.put("userId", command.getUserId());
        run.put("input", command.getInput());
        run.put("context", sanitizeStructuredValue(command.getContext()));
        run.put("maxTurns", command.getMaxTurns());
        run.put("timeoutMs", command.getTimeoutMs());

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("protocolVersion", StringUtils.hasText(snapshot.getProtocolVersion())
                ? snapshot.getProtocolVersion()
                : PROTOCOL_VERSION);
        runtime.put("run", run);
        runtime.put("rootAgent", snapshot.getRootAgent());
        runtime.put("agentGraph", snapshot.getAgentGraph());
        runtime.put("resolvedCapabilities", snapshot.getResolvedCapabilities());
        runtime.put("workflowSnapshot", snapshot.getWorkflowSnapshot());
        runtime.put("snapshotHash", snapshot.getSnapshotHash());
        runtime.put("modelSettings", connection == null ? Map.of() : connection.getSettings());
        request.getExt().put("agentRuntime", runtime);
        return request;
    }

    private Map<String, Object> runtimeExtension(Map<String, Object> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return Map.of();
        }
        Object nested = extensions.get("agentRuntime");
        if (nested instanceof Map<?, ?> nestedMap) {
            Map<String, Object> runtime = new LinkedHashMap<>();
            nestedMap.forEach((key, value) -> {
                if (key instanceof String stringKey) {
                    runtime.put(stringKey, value);
                }
            });
            return runtime;
        }
        Map<String, Object> runtime = new LinkedHashMap<>();
        for (String key : RUNTIME_EXTENSION_KEYS) {
            if (extensions.containsKey(key)) {
                runtime.put(key, extensions.get(key));
            }
        }
        return runtime;
    }

    private Map<String, Object> legacyExtensions(Map<String, Object> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> legacy = new LinkedHashMap<>(extensions);
        legacy.remove("agentRuntime");
        RUNTIME_EXTENSION_KEYS.forEach(legacy::remove);
        Object sanitized = sanitizeValue(legacy);
        if (sanitized instanceof Map<?, ?> sanitizedMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            sanitizedMap.forEach((key, value) -> {
                if (key instanceof String stringKey) {
                    result.put(stringKey, value);
                }
            });
            return result;
        }
        return Map.of();
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof JsonNode node) {
            return sanitizeValue(objectMapper.convertValue(node, Object.class));
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String stringKey) || isSensitiveKey(stringKey)) {
                    return;
                }
                sanitized.put(stringKey, sanitizeValue(item));
            });
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> sanitized = new ArrayList<>(collection.size());
            collection.forEach(item -> sanitized.add(sanitizeValue(item)));
            return sanitized;
        }
        return value;
    }

    private Object sanitizeStructuredValue(Object value) {
        if (value == null) {
            return null;
        }
        return sanitizeValue(objectMapper.convertValue(value, Object.class));
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return SENSITIVE_EXTENSION_KEYS.contains(normalized)
                || normalized.endsWith("apikey")
                || normalized.endsWith("token")
                || normalized.endsWith("password")
                || normalized.endsWith("secret")
                || normalized.endsWith("credential")
                || normalized.endsWith("authorization")
                || normalized.endsWith("cookie");
    }

    private boolean waitFor(Process process,
                            long timeoutMs,
                            AgentCancellation cancellation,
                            CompletableFuture<?> stdoutFuture)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (true) {
            if (cancellation.isCancellationRequested()) {
                process.destroyForcibly();
                throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, "agent run cancelled");
            }
            if (stdoutFuture.isCompletedExceptionally()) {
                process.destroyForcibly();
                stdoutFuture.join();
            }
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return false;
            }
            long waitMillis = Math.max(1L, Math.min(100L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
            if (process.waitFor(waitMillis, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
    }

    private Double doubleValue(Object... values) {
        for (Object value : values) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value != null) {
                try {
                    return Double.parseDouble(value.toString());
                } catch (NumberFormatException ignored) {
                    // Try the next alias.
                }
            }
        }
        return null;
    }

    private Integer integerValue(Object... values) {
        for (Object value : values) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value != null) {
                try {
                    return Integer.parseInt(value.toString());
                } catch (NumberFormatException ignored) {
                    // Try the next alias.
                }
            }
        }
        return null;
    }

    private long resolveTimeoutMs(AiAgentProperties properties, ProviderChatRequest request) {
        Integer requestTimeout = request.getTimeoutMs();
        if (requestTimeout != null && requestTimeout > 0) {
            return requestTimeout;
        }
        Integer configured = properties.getTimeoutMs();
        if (configured != null && configured > 0) {
            return configured;
        }
        return Duration.ofSeconds(120).toMillis();
    }

    private void validate(String apiKey, String workerCommand) {
        if (!StringUtils.hasText(apiKey)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_API_KEY);
        }
        if (!StringUtils.hasText(workerCommand)) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "agent worker command is required");
        }
    }

    private String resolveValue(String requestValue, String fallback) {
        return StringUtils.hasText(requestValue) ? requestValue.trim() : fallback;
    }

    private Path resolveScriptPath(AiAgentProperties properties) {
        if (StringUtils.hasText(properties.getScriptPath())) {
            Path configuredPath = Path.of(properties.getScriptPath()).toAbsolutePath().normalize();
            log.debug("ai agent script path resolved from configuration, scriptPath={}", configuredPath);
            return configuredPath;
        }
        Path deployedPath = DEPLOYED_SCRIPT_PATH.toAbsolutePath().normalize();
        if (Files.exists(deployedPath)) {
            log.debug("ai agent script path resolved from deployed path, scriptPath={}", deployedPath);
            return deployedPath;
        }
        Path developmentPath = DEV_SCRIPT_PATH.toAbsolutePath().normalize();
        if (Files.exists(developmentPath)) {
            log.debug("ai agent script path resolved from development path, scriptPath={}", developmentPath);
            return developmentPath;
        }
        throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_AI_AGENT_SCRIPT_PATH);
    }

    private String safeError(String stderr, String credential) {
        if (!StringUtils.hasText(stderr)) {
            return "unknown error";
        }
        String normalized = stderr.trim();
        if (StringUtils.hasText(credential)) {
            normalized = normalized.replace(credential, "[REDACTED_CREDENTIAL]");
        }
        normalized = BEARER_TOKEN.matcher(normalized).replaceAll("Bearer [REDACTED]");
        normalized = OPENAI_KEY.matcher(normalized).replaceAll("[REDACTED_OPENAI_KEY]");
        if (normalized.length() > 500) {
            return normalized.substring(0, 500);
        }
        return normalized;
    }
}
