package ai.platform.aiassit.service.ai.agent.service;

import ai.platform.aiassit.service.ai.agent.config.AiAgentProperties;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.spi.agent.AgentCancellation;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunCommand;
import com.fasterxml.jackson.databind.JsonNode;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Consumer;

/** Launches the bundled OpenAI Agents TypeScript worker with protocol v2. */
@Component
public class AiAgentTypescriptProcessExecutor {

    private static final String CLASSPATH_SCRIPT_PATH = "typescript/dist/worker.mjs";

    private static final Path DEPLOYED_SCRIPT_PATH = Path.of(
            "typescript",
            "dist",
            "worker.mjs"
    );

    private static final Path DEV_SCRIPT_PATH = Path.of(
            "app",
            "app-platform-chat",
            "providers",
            "ai-provider-ai-agent",
            "src",
            "main",
            "typescript",
            "dist",
            "worker.mjs"
    );

    private final AiAgentProcessExecutor processExecutor;
    private volatile Path extractedScriptPath;

    public AiAgentTypescriptProcessExecutor(AiAgentProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    public JsonNode executeAgent(AiAgentProperties properties,
                                 AgentDefinitionSnapshot snapshot,
                                 AgentRunCommand command,
                                 Consumer<JsonNode> frameConsumer,
                                 AgentCancellation cancellation) {
        Map<String, String> additionalEnvironment = properties.isTypescriptDryRun()
                ? Map.of("AI_AGENT_DRY_RUN", "1")
                : Map.of();
        return processExecutor.executeAgentWithWorker(
                properties,
                snapshot,
                command,
                frameConsumer,
                cancellation,
                properties.getNodeCommand(),
                resolveScriptPath(properties),
                properties.getTypescriptWorkingDirectory(),
                additionalEnvironment
        );
    }

    Path resolveScriptPath(AiAgentProperties properties) {
        return resolveScriptPath(properties, Path.of(""));
    }

    Path resolveScriptPath(AiAgentProperties properties, Path startDirectory) {
        if (StringUtils.hasText(properties.getTypescriptScriptPath())) {
            Path configuredPath = Path.of(properties.getTypescriptScriptPath()).toAbsolutePath().normalize();
            if (Files.isRegularFile(configuredPath)) {
                return configuredPath;
            }
            throw BizException.of(
                    AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID,
                    "typescript worker bundle does not exist"
            );
        }
        Path deployedPath = AgentWorkerPathResolver.findFromCurrentOrAncestor(startDirectory, DEPLOYED_SCRIPT_PATH);
        if (deployedPath != null) {
            return deployedPath;
        }
        Path developmentPath = AgentWorkerPathResolver.findFromCurrentOrAncestor(startDirectory, DEV_SCRIPT_PATH);
        if (developmentPath != null) {
            return developmentPath;
        }
        Path classpathPath = extractClasspathWorker();
        if (classpathPath != null) {
            return classpathPath;
        }
        throw BizException.of(
                AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID,
                "typescript worker bundle is required"
        );
    }

    private Path extractClasspathWorker() {
        Path current = extractedScriptPath;
        if (current != null && Files.isRegularFile(current)) {
            return current;
        }
        synchronized (this) {
            current = extractedScriptPath;
            if (current != null && Files.isRegularFile(current)) {
                return current;
            }
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(CLASSPATH_SCRIPT_PATH)) {
                if (input == null) {
                    return null;
                }
                Path directory = Files.createTempDirectory("ai-agent-typescript-");
                Path worker = directory.resolve("worker.mjs");
                Files.copy(input, worker, StandardCopyOption.REPLACE_EXISTING);
                directory.toFile().deleteOnExit();
                worker.toFile().deleteOnExit();
                extractedScriptPath = worker;
                return worker;
            } catch (IOException error) {
                throw BizException.of(
                        AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID,
                        "typescript worker bundle could not be extracted"
                );
            }
        }
    }
}
