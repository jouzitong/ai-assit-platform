package ai.platform.aiassit.agent.runtime.tool;

import ai.platform.aiassit.service.ai.spi.tool.ManagedToolExecutionRequest;
import ai.platform.aiassit.service.ai.spi.tool.ManagedToolExecutionResult;
import ai.platform.aiassit.service.ai.spi.tool.ManagedToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.thread.AsyncTaskExcutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Executes one immutable Tool version in a short-lived Python or Node.js child process. */
@Component
@Slf4j
public class ManagedToolProcessExecutor implements ManagedToolExecutor {

    private static final String RESULT_PREFIX = "__AI_TOOL_RESULT__";
    private static final int MAX_SOURCE_BYTES = 512 * 1024;
    private static final int MAX_STREAM_BYTES = 1024 * 1024;
    private static final Pattern PYTHON_ENTRYPOINT = Pattern.compile(
            "(?m)^\\s*(?:async\\s+)?def\\s+run\\s*\\(");
    private static final Pattern PYTHON_SDK_TOOL = Pattern.compile(
            "(?m)^\\s*@function_tool(?:\\s*\\(|\\s*$)");
    private static final Pattern JAVASCRIPT_ENTRYPOINT = Pattern.compile(
            "(?m)^\\s*export\\s+(?:async\\s+)?function\\s+run\\s*\\(");
    private static final Pattern JAVASCRIPT_SDK_TOOL = Pattern.compile(
            "(?m)(?:^|[=:(,]\\s*)tool\\s*\\(\\s*\\{");

    private final ObjectMapper objectMapper;
    private final String pythonCommand;
    private final String nodeCommand;
    private final Executor taskExecutor;

    public ManagedToolProcessExecutor(ObjectMapper objectMapper,
                                      @Value("${ai.tool.runtime.python-command:python3}") String pythonCommand,
                                      @Value("${ai.tool.runtime.node-command:node}") String nodeCommand) {
        this(objectMapper, pythonCommand, nodeCommand, Runnable::run);
    }

    @Autowired
    public ManagedToolProcessExecutor(ObjectMapper objectMapper,
                                      @Value("${ai.tool.runtime.python-command:python3}") String pythonCommand,
                                      @Value("${ai.tool.runtime.node-command:node}") String nodeCommand,
                                      AsyncTaskExcutor asyncTaskExcutor) {
        this(objectMapper, pythonCommand, nodeCommand, asyncTaskExcutor::submit);
    }

    private ManagedToolProcessExecutor(ObjectMapper objectMapper,
                                       String pythonCommand,
                                       String nodeCommand,
                                       Executor taskExecutor) {
        this.objectMapper = objectMapper;
        this.pythonCommand = pythonCommand;
        this.nodeCommand = nodeCommand;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public List<String> validate(Map<String, Object> definition) {
        List<String> errors = structuralErrors(definition);
        if (!errors.isEmpty()) return errors;
        Path directory = null;
        try {
            directory = Files.createTempDirectory("managed-tool-validate-");
            RuntimeFiles files = prepareFiles(directory, definition);
            List<String> command = isPython(definition)
                    ? List.of(pythonCommand, "-m", "py_compile", files.source().toString())
                    : List.of(nodeCommand, "--check", files.source().toString());
            ProcessResult result = runProcess(command, directory, null, Map.of(), 15_000);
            if (result.exitCode() != 0) {
                errors.add(compact(result.stderr().isBlank() ? result.stdout() : result.stderr()));
            }
        } catch (Exception ex) {
            errors.add("Tool runtime validation failed: " + compact(ex.getMessage()));
        } finally {
            deleteDirectory(directory);
        }
        return errors;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> describe(Map<String, Object> definition) {
        List<String> errors = validate(definition);
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.get(0));
        Path directory = null;
        try {
            directory = Files.createTempDirectory("managed-tool-describe-");
            RuntimeFiles files = prepareFiles(directory, definition);
            List<String> command = isPython(definition)
                    ? List.of(pythonCommand, files.runner().toString(), files.source().toString(), "--describe")
                    : List.of(nodeCommand, files.runner().toString(), files.source().toString(), "--describe");
            ProcessResult process = runProcess(command, directory, null, Map.of(), 15_000);
            if (process.exitCode() != 0) {
                throw new IllegalStateException(compact(process.stderr().isBlank()
                        ? process.stdout() : process.stderr()));
            }
            Object output = parseOutput(process.stdout()).output();
            if (!(output instanceof Map<?, ?> map)) {
                throw new IllegalStateException("Tool definition metadata must be a JSON object");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Managed Tool description failed: " + compact(ex.getMessage()), ex);
        } finally {
            deleteDirectory(directory);
        }
    }

    @Override
    public ManagedToolExecutionResult execute(ManagedToolExecutionRequest request) {
        Map<String, Object> definition = request == null ? Map.of() : safeMap(request.getDefinition());
        List<String> errors = validate(definition);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.get(0));
        }
        Path directory = null;
        long startedAt = System.currentTimeMillis();
        try {
            directory = Files.createTempDirectory("managed-tool-run-");
            RuntimeFiles files = prepareFiles(directory, definition);
            List<String> command = isPython(definition)
                    ? List.of(pythonCommand, files.runner().toString(), files.source().toString())
                    : List.of(nodeCommand, files.runner().toString(), files.source().toString());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("arguments", request == null ? Map.of() : safeMap(request.getArguments()));
            payload.put("context", request == null ? Map.of() : safeMap(request.getContext()));
            Map<String, String> environment = tokenEnvironment(request == null ? null : request.getExecutionToken());
            ProcessResult process = runProcess(command, directory, objectMapper.writeValueAsBytes(payload),
                    environment, timeoutMs(definition));
            if (process.exitCode() != 0) {
                throw new IllegalStateException(compact(process.stderr().isBlank()
                        ? process.stdout() : process.stderr()));
            }
            ParsedOutput parsed = parseOutput(process.stdout());
            return ManagedToolExecutionResult.builder()
                    .output(parsed.output())
                    .stdout(parsed.logs())
                    .stderr(process.stderr())
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .build();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Managed Tool execution failed: " + compact(ex.getMessage()), ex);
        } finally {
            deleteDirectory(directory);
        }
    }

    private List<String> structuralErrors(Map<String, Object> definition) {
        List<String> errors = new ArrayList<>();
        String runtime = text(definition.get("implementationRuntime"));
        String source = text(definition.get("sourceCode"));
        if (!"PYTHON".equals(runtime) && !"JAVASCRIPT".equals(runtime)) {
            errors.add("implementationRuntime must be PYTHON or JAVASCRIPT");
        }
        if (!StringUtils.hasText(source)) {
            errors.add("sourceCode is required");
        } else if (source.getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES) {
            errors.add("sourceCode exceeds 524288 bytes");
        } else if ("PYTHON".equals(runtime) && !PYTHON_ENTRYPOINT.matcher(source).find()
                && !PYTHON_SDK_TOOL.matcher(source).find()) {
            errors.add("Python Tool must use @function_tool or define run(arguments, context)");
        } else if ("JAVASCRIPT".equals(runtime) && !JAVASCRIPT_ENTRYPOINT.matcher(source).find()
                && !JAVASCRIPT_SDK_TOOL.matcher(source).find()) {
            errors.add("JavaScript Tool must use tool({...}) or export run(args, context)");
        }
        return errors;
    }

    private RuntimeFiles prepareFiles(Path directory, Map<String, Object> definition) throws IOException {
        boolean python = isPython(definition);
        Path source = directory.resolve(python ? "tool.py" : "tool.mjs");
        Path runner = directory.resolve(python ? "runner.py" : "runner.mjs");
        Files.writeString(source, text(definition.get("sourceCode")), StandardCharsets.UTF_8);
        String resource = python ? "tool-runtime/python_runner.py" : "tool-runtime/javascript_runner.mjs";
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            Files.copy(input, runner, StandardCopyOption.REPLACE_EXISTING);
        }
        if (python) {
            copyResource("tool-runtime/python_agents_shim.py", directory.resolve("agents/__init__.py"));
        } else {
            Path packageDirectory = directory.resolve("node_modules/@openai/agents");
            copyResource("tool-runtime/javascript_agents_shim.mjs", packageDirectory.resolve("index.mjs"));
            Files.writeString(packageDirectory.resolve("package.json"),
                    "{\"name\":\"@openai/agents\",\"type\":\"module\",\"exports\":\"./index.mjs\"}",
                    StandardCharsets.UTF_8);
        }
        return new RuntimeFiles(source, runner);
    }

    private void copyResource(String resource, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ProcessResult runProcess(List<String> command,
                                     Path directory,
                                     byte[] stdin,
                                     Map<String, String> additionalEnvironment,
                                     int timeoutMs) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        Map<String, String> environment = builder.environment();
        String path = environment.get("PATH");
        String lang = environment.get("LANG");
        String home = environment.get("HOME");
        environment.clear();
        if (StringUtils.hasText(path)) environment.put("PATH", path);
        if (StringUtils.hasText(lang)) environment.put("LANG", lang);
        if (StringUtils.hasText(home)) environment.put("HOME", home);
        environment.put("PYTHONUNBUFFERED", "1");
        environment.putAll(additionalEnvironment);
        Process process = builder.start();
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(
                () -> readLimited(process.getInputStream()), taskExecutor);
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(
                () -> readLimited(process.getErrorStream()), taskExecutor);
        try (OutputStream output = process.getOutputStream()) {
            if (stdin != null) output.write(stdin);
        }
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Tool process timed out after " + timeoutMs + "ms");
        }
        return new ProcessResult(process.exitValue(), stdout.join(), stderr.join());
    }

    private String readLimited(InputStream input) {
        try (input) {
            byte[] value = input.readNBytes(MAX_STREAM_BYTES + 1);
            if (value.length > MAX_STREAM_BYTES) {
                throw new IllegalStateException("Tool process output exceeds 1048576 bytes");
            }
            return new String(value, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read Tool process output", ex);
        }
    }

    private ParsedOutput parseOutput(String stdout) throws IOException {
        String resultJson = null;
        List<String> logs = new ArrayList<>();
        for (String line : stdout.split("\\R", -1)) {
            if (line.startsWith(RESULT_PREFIX)) {
                resultJson = line.substring(RESULT_PREFIX.length());
            } else if (!line.isEmpty()) {
                logs.add(line);
            }
        }
        if (resultJson == null) throw new IllegalStateException("Tool process returned no result");
        return new ParsedOutput(objectMapper.readValue(resultJson, Object.class), String.join("\n", logs));
    }

    private Map<String, String> tokenEnvironment(String token) {
        if (!StringUtils.hasText(token)) return Map.of();
        return Map.of(
                "AI_AGENT_KB_SEARCH_TOKEN", token,
                "AI_AGENT_TOOL_GATEWAY_TOKEN", token,
                "AI_AGENT_SKILL_GATEWAY_TOKEN", token);
    }

    private int timeoutMs(Map<String, Object> definition) {
        Object value = definition.get("timeoutMs");
        int timeout = value instanceof Number number ? number.intValue() : 30_000;
        return Math.max(100, Math.min(timeout, 300_000));
    }

    private boolean isPython(Map<String, Object> definition) {
        return "PYTHON".equals(text(definition.get("implementationRuntime")));
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : new LinkedHashMap<>(value);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String compact(String value) {
        String sanitized = value == null ? "unknown error" : value
                .replaceAll("(?i)bearer\\s+[^\\s,;]+", "Bearer [REDACTED]")
                .replaceAll("\\bsk-[A-Za-z0-9_-]{8,}\\b", "[REDACTED]");
        return sanitized.substring(0, Math.min(sanitized.length(), 1000));
    }

    private void deleteDirectory(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.debug("Unable to delete managed Tool temporary path: {}", path);
                }
            });
        } catch (IOException ex) {
            log.debug("Unable to clean managed Tool temporary directory: {}", directory);
        }
    }

    private record RuntimeFiles(Path source, Path runner) { }
    private record ProcessResult(int exitCode, String stdout, String stderr) { }
    private record ParsedOutput(Object output, String logs) { }
}
