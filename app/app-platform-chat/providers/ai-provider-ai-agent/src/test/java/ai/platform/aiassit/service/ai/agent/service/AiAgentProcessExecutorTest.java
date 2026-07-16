package ai.platform.aiassit.service.ai.agent.service;

import ai.platform.aiassit.service.ai.agent.config.AiAgentProperties;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAgentProcessExecutorTest {

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void terminatesTheWorkerAtTheRunTimeout() throws Exception {
        Path worker = shellWorker("sleep 5\n");
        AiAgentProperties properties = properties(75);
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> new AiAgentProcessExecutor(objectMapper).executeAgentWithWorker(
                properties,
                new AgentDefinitionSnapshot(),
                command(),
                frame -> { },
                () -> false,
                "/bin/sh",
                worker,
                "",
                Map.of()
        )).isInstanceOf(RuntimeException.class);

        assertThat((System.nanoTime() - startedAt) / 1_000_000).isLessThan(2000L);
    }

    @Test
    void doesNotStartAWorkerWhenCancellationWasAlreadyRequested() throws Exception {
        Path marker = tempDirectory.resolve("started");
        Path worker = shellWorker("touch \"" + marker + "\"\n");

        assertThatThrownBy(() -> new AiAgentProcessExecutor(objectMapper).executeAgentWithWorker(
                properties(5000),
                new AgentDefinitionSnapshot(),
                command(),
                frame -> { },
                () -> true,
                "/bin/sh",
                worker,
                "",
                Map.of()
        )).isInstanceOf(RuntimeException.class);

        assertThat(marker).doesNotExist();
    }

    @Test
    void abortsTheWorkerWhenTheEventObserverFails() throws Exception {
        Path worker = shellWorker("printf '%s\\n' '{\"type\":\"event\",\"eventType\":\"agent.started\"}'\nsleep 5\n");
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> new AiAgentProcessExecutor(objectMapper).executeAgentWithWorker(
                properties(5000),
                new AgentDefinitionSnapshot(),
                command(),
                frame -> { throw new IllegalStateException("observer failed"); },
                () -> false,
                "/bin/sh",
                worker,
                "",
                Map.of()
        )).isInstanceOf(RuntimeException.class);

        assertThat((System.nanoTime() - startedAt) / 1_000_000).isLessThan(2000L);
    }

    @Test
    void forwardsGatewayEndpointsAndRunScopedTokensToTheWorkerEnvironment() throws Exception {
        Path worker = shellWorker("cat >/dev/null\n"
                + "printf '%s\\n' \"{\\\"type\\\":\\\"result\\\",\\\"data\\\":{"
                + "\\\"toolUrl\\\":\\\"$AI_AGENT_TOOL_GATEWAY_URL\\\","
                + "\\\"skillUrl\\\":\\\"$AI_AGENT_SKILL_GATEWAY_URL\\\","
                + "\\\"toolToken\\\":\\\"$AI_AGENT_TOOL_GATEWAY_TOKEN\\\","
                + "\\\"skillToken\\\":\\\"$AI_AGENT_SKILL_GATEWAY_TOKEN\\\"}}\"\n");
        AiAgentProperties properties = properties(5000);

        var result = new AiAgentProcessExecutor(objectMapper).executeAgentWithWorker(
                properties,
                new AgentDefinitionSnapshot(),
                command(),
                frame -> { },
                () -> false,
                "/bin/sh",
                worker,
                "",
                Map.of(
                        "AI_AGENT_TOOL_GATEWAY_TOKEN", "tool-token",
                        "AI_AGENT_SKILL_GATEWAY_TOKEN", "skill-token"
                )
        );

        assertThat(result.path("toolUrl").asText()).isEqualTo("http://127.0.0.1:9764/chat");
        assertThat(result.path("skillUrl").asText()).isEqualTo("http://127.0.0.1:9764/chat");
        assertThat(result.path("toolToken").asText()).isEqualTo("tool-token");
        assertThat(result.path("skillToken").asText()).isEqualTo("skill-token");
    }

    private AiAgentProperties properties(int timeoutMs) {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setApiKey("test-api-key");
        properties.setTimeoutMs(timeoutMs);
        return properties;
    }

    private AgentRunCommand command() {
        AgentRunCommand command = new AgentRunCommand();
        command.setRunId("run-test");
        command.setRequestId("request-test");
        command.setInput("test");
        return command;
    }

    private Path shellWorker(String body) throws Exception {
        Path worker = tempDirectory.resolve("worker-" + System.nanoTime() + ".sh");
        Files.writeString(worker, "#!/bin/sh\n" + body);
        return worker;
    }
}
