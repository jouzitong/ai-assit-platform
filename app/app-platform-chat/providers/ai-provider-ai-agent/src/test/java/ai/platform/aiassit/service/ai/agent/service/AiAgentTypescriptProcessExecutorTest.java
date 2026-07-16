package ai.platform.aiassit.service.ai.agent.service;

import ai.platform.aiassit.service.ai.agent.config.AiAgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AiAgentTypescriptProcessExecutorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void resolvesDevelopmentWorkerWhenApplicationStartsFromChatModuleDirectory() throws Exception {
        Path repositoryRoot = tempDirectory.resolve("repository");
        Path chatModuleDirectory = repositoryRoot.resolve("app/app-platform-chat");
        Path worker = repositoryRoot.resolve(
                "app/app-platform-chat/providers/ai-provider-ai-agent/src/main/typescript/dist/worker.mjs");
        Files.createDirectories(worker.getParent());
        Files.createFile(worker);

        AiAgentTypescriptProcessExecutor executor = new AiAgentTypescriptProcessExecutor(
                new AiAgentProcessExecutor(new ObjectMapper()));

        assertThat(executor.resolveScriptPath(new AiAgentProperties(), chatModuleDirectory))
                .isEqualTo(worker.toAbsolutePath().normalize());
    }
}
