package ai.platform.aiassit.service.ai.agent.service;

import ai.platform.aiassit.service.ai.agent.config.AiAgentProperties;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.context.SystemContext;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
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
        validate(properties);
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
        env.put("OPENAI_API_KEY", properties.getApiKey());
        if (StringUtils.hasText(properties.getBaseUrl())) {
            env.put("OPENAI_BASE_URL", properties.getBaseUrl());
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

        try {
            log.info("ai agent process starting, command={}", command);
            Process process = processBuilder.start();
            log.info("ai agent process started");
            try (Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                objectMapper.writeValue(writer, buildPayload(properties, request));
            }
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                log.warn("ai agent process timeout, timeoutMs={}, scriptPath={}", timeoutMs, scriptPath);
                process.destroyForcibly();
                throw new IllegalStateException("ai agent python process timeout");
            }
            String stdout = readAll(process.getInputStream());
            String stderr = readAll(process.getErrorStream());
            int exitValue = process.exitValue();
            log.info("ai agent process finished, exitValue={}, stdoutLength={}, stderrLength={}",
                    exitValue, stdout.length(), stderr.length());
            if (exitValue != 0) {
                log.error("ai agent process failed, exitValue={}, stderr={}", exitValue, safeError(stderr));
                throw new IllegalStateException("ai agent python process failed: " + safeError(stderr));
            }
            if (!StringUtils.hasText(stdout)) {
                log.error("ai agent process returned empty output, stderr={}", safeError(stderr));
                throw new IllegalStateException("ai agent python process returned empty output");
            }
            JsonNode result = objectMapper.readTree(stdout);
            log.info("ai agent process parsed output successfully");
            return result;
        } catch (Exception ex) {
            log.error("ai agent provider execute failed, scriptPath={}, model={}",
                    scriptPath,
                    StringUtils.hasText(request.getModel()) ? request.getModel() : properties.getDefaultModel(),
                    ex);
            throw new IllegalStateException("ai agent provider execute failed", ex);
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

    private void validate(AiAgentProperties properties) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("ai.provider.ai-agent.api-key must not be empty");
        }
        if (!StringUtils.hasText(properties.getPythonCommand())) {
            throw new IllegalStateException("ai.provider.ai-agent.python-command must not be empty");
        }
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
        throw new IllegalStateException(
                "ai.provider.ai-agent.script-path must not be empty when the external python project is not available");
    }

    private String readAll(java.io.InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int len;
            while ((len = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, len);
            }
        }
        return builder.toString();
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
