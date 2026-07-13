package ai.platform.aiassit.service.ai.agent.service;

import ai.platform.aiassit.service.ai.agent.config.AiAgentProperties;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
@Component
public class AiAgentProcessExecutor {

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
        String apiKey = resolveValue(request == null ? null : request.getApiKey(), properties.getApiKey());
        String baseUrl = resolveValue(request == null ? null : request.getBaseUrl(), properties.getBaseUrl());
        validate(properties, apiKey);
        Path scriptPath = resolveScriptPath(properties);
        long timeoutMs = resolveTimeoutMs(properties, request);
        log.info("ai agent process preparing, scriptPath={}, pythonCommand={}, model={}, timeoutMs={}, workingDirectory={}",
                scriptPath, properties.getPythonCommand(),
                StringUtils.hasText(request.getModel()) ? request.getModel() : properties.getDefaultModel(),
                timeoutMs, properties.getWorkingDirectory());
        List<String> command = new ArrayList<>();
        command.add(properties.getPythonCommand());
        command.add(scriptPath.toString());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (StringUtils.hasText(properties.getWorkingDirectory())) {
            processBuilder.directory(Path.of(properties.getWorkingDirectory()).toFile());
        }
        Map<String, String> env = processBuilder.environment();
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
        if (StringUtils.hasText(properties.getValidateContentType())) {
            env.put("AI_AGENT_VALIDATE_CONTENT_TYPE", properties.getValidateContentType());
        }
        if (StringUtils.hasText(properties.getValidateStructure())) {
            env.put("AI_AGENT_VALIDATE_STRUCTURE", properties.getValidateStructure());
        }
        env.put("AI_AGENT_KB_SEARCH_TOKEN", ((UserContext) SystemContext.getUserContext()).token());

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
            boolean finished = activeProcess.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                log.warn("ai agent process timeout, timeoutMs={}, scriptPath={}", timeoutMs, scriptPath);
                activeProcess.destroyForcibly();
                throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, "python process timeout");
            }
            stdoutFuture.join();
            String stderr = stderrFuture.join();
            int exitValue = activeProcess.exitValue();
            log.info("ai agent process finished, exitValue={}, stderrLength={}", exitValue, stderr.length());
            if (exitValue != 0) {
                log.error("ai agent process failed, exitValue={}, stderr={}", exitValue, safeError(stderr));
                throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, safeError(stderr));
            }
            JsonNode result = resultRef.get();
            if (result == null) {
                log.error("ai agent process returned empty output, stderr={}", safeError(stderr));
                throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "empty output");
            }
            log.info("ai agent process parsed output successfully");
            return result;
        } catch (InterruptedException ex) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, "python process interrupted");
        } catch (Exception ex) {
            if (ex instanceof BizException bizException) {
                throw bizException;
            }
            log.error("ai agent provider execute failed, scriptPath={}, model={}",
                    scriptPath,
                    StringUtils.hasText(request.getModel()) ? request.getModel() : properties.getDefaultModel(),
                    ex);
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, ex.getMessage());
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

    private Map<String, Object> buildPayload(AiAgentProperties properties, ProviderChatRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", StringUtils.hasText(request.getModel()) ? request.getModel() : properties.getDefaultModel());
        payload.put("messages", request.getMessages());
        payload.put("tools", request.getTools());
        payload.put("responseFormat", request.getResponseFormat());
        payload.put("meta", request.getMeta());
        payload.put("ext", request.getExt());
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", request.getTemperature());
        options.put("topP", request.getTopP());
        options.put("maxTokens", request.getMaxTokens());
        options.put("timeoutMs", resolveTimeoutMs(properties, request));
        payload.put("options", options);
        return payload;
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

    private void validate(AiAgentProperties properties, String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_API_KEY);
        }
        if (!StringUtils.hasText(properties.getPythonCommand())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_AI_AGENT_PYTHON_COMMAND);
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

    private String safeError(String stderr) {
        if (!StringUtils.hasText(stderr)) {
            return "unknown error";
        }
        String normalized = stderr.trim();
        if (normalized.length() > 500) {
            return normalized.substring(0, 500);
        }
        return normalized;
    }
}
