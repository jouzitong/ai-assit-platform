package ai.platform.aiassit.service.ai.agent.service;

import ai.platform.aiassit.service.ai.agent.config.AiAgentProperties;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunCommand;
import ai.platform.aiassit.service.ai.spi.agent.AgentTemporaryTokenIssuer;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.arthena.framework.common.context.SystemContext;
import org.athena.framework.security.api.model.MutableUserContext;
import org.athena.framework.security.api.model.Subject;
import org.athena.framework.security.api.model.UserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAgentProcessExecutorTest {

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolvesDevelopmentWorkerWhenApplicationStartsFromChatModuleDirectory() throws Exception {
        Path repositoryRoot = tempDirectory.resolve("repository");
        Path chatModuleDirectory = repositoryRoot.resolve("app/app-platform-chat");
        Path worker = repositoryRoot.resolve(
                "app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/main.py");
        Files.createDirectories(worker.getParent());
        Files.createFile(worker);

        Path resolved = new AiAgentProcessExecutor(objectMapper)
                .resolveScriptPath(new AiAgentProperties(), chatModuleDirectory);

        assertThat(resolved).isEqualTo(worker.toAbsolutePath().normalize());
    }

    @Test
    void prefersProjectVirtualEnvironmentForTheDefaultPythonCommand() throws Exception {
        Path projectDirectory = tempDirectory.resolve("python-project");
        Path worker = projectDirectory.resolve("agent_provider/main.py");
        Path localPython = projectDirectory.resolve(".venv/bin/python");
        Files.createDirectories(worker.getParent());
        Files.createDirectories(localPython.getParent());
        Files.createFile(worker);
        Files.createFile(localPython);

        String resolved = new AiAgentProcessExecutor(objectMapper)
                .resolvePythonCommand(new AiAgentProperties(), worker);

        assertThat(resolved).isEqualTo(localPython.toAbsolutePath().normalize().toString());
    }

    @Test
    void keepsAnExplicitPythonCommand() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setPythonCommand("/opt/runtime/bin/python");

        String resolved = new AiAgentProcessExecutor(objectMapper)
                .resolvePythonCommand(properties, tempDirectory.resolve("agent_provider/main.py"));

        assertThat(resolved).isEqualTo("/opt/runtime/bin/python");
    }

    @Test
    void explainsHowToFixAMissingOpenAiAgentsDependency() throws Exception {
        Path worker = shellWorker("printf '%s\\n' \"{\\\"type\\\":\\\"error\\\","
                + "\\\"message\\\":\\\"No module named 'agents'\\\"}\"\n");

        assertThatThrownBy(() -> new AiAgentProcessExecutor(objectMapper).executeAgentWithWorker(
                properties(5000),
                new AgentDefinitionSnapshot(),
                command(),
                frame -> { },
                () -> false,
                "/bin/sh",
                worker,
                "",
                Map.of()
        )).satisfies(error -> assertThat(error.toString())
                .contains("AI_PROVIDER_AI_AGENT_PYTHON_COMMAND")
                .contains("openai-agents==0.18.2"));
    }

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
    void forwardsOnlyTheChatBaseAndRunScopedTokensToTheWorkerEnvironment() throws Exception {
        Path worker = shellWorker("cat >/dev/null\n"
                + "printf '%s\\n' \"{\\\"type\\\":\\\"result\\\",\\\"data\\\":{"
                + "\\\"chatBaseUrl\\\":\\\"$AI_AGENT_CHAT_BASE_URL\\\","
                + "\\\"kbSearchUrl\\\":\\\"$AI_AGENT_KB_SEARCH_URL\\\","
                + "\\\"dataPreviewUrl\\\":\\\"$AI_AGENT_DATA_PREVIEW_URL\\\","
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
                        "AI_AGENT_CHAT_BASE_URL", "http://gateway/untrusted-chat-base",
                        "AI_AGENT_TOOL_GATEWAY_URL", "http://tool-service/direct",
                        "AI_AGENT_SKILL_GATEWAY_URL", "http://skill-service/direct",
                        "AI_AGENT_KB_SEARCH_URL", "http://gateway/legacy-kb-search",
                        "AI_AGENT_DATA_PREVIEW_URL", "http://db-engine/direct-preview",
                        "AI_AGENT_TOOL_GATEWAY_TOKEN", "tool-token",
                        "AI_AGENT_SKILL_GATEWAY_TOKEN", "skill-token"
                )
        );

        assertThat(result.path("chatBaseUrl").asText()).isEqualTo("http://127.0.0.1:13103/chat");
        assertThat(result.path("toolUrl").asText()).isEqualTo("http://127.0.0.1:13103/chat");
        assertThat(result.path("skillUrl").asText()).isEqualTo("http://127.0.0.1:13103/chat");
        assertThat(result.path("kbSearchUrl").asText()).isEmpty();
        assertThat(result.path("dataPreviewUrl").asText()).isEmpty();
        assertThat(result.path("toolToken").asText()).isEqualTo("tool-token");
        assertThat(result.path("skillToken").asText()).isEqualTo("skill-token");
    }

    @Test
    void bindsTheTemporaryWorkerTokenToTheAgentRun() throws Exception {
        Path worker = shellWorker("cat >/dev/null\n"
                + "printf '%s\\n' \"{\\\"type\\\":\\\"result\\\",\\\"data\\\":{"
                + "\\\"platformToken\\\":\\\"$AI_AGENT_PLATFORM_TOKEN\\\"}}\"\n");
        AtomicReference<String> issuedRunId = new AtomicReference<>();
        AgentTemporaryTokenIssuer issuer = new AgentTemporaryTokenIssuer() {
            @Override
            public String issue(UserContext userContext) {
                return "legacy-token";
            }

            @Override
            public String issue(UserContext userContext, String agentRunId) {
                issuedRunId.set(agentRunId);
                return "run-bound-token";
            }
        };
        MutableUserContext userContext = new MutableUserContext();
        userContext.setSubject(new Subject(7L, "agent-user", "default", "USER"));
        SystemContext.setUserContext(userContext);

        try {
            var result = new AiAgentProcessExecutor(objectMapper, issuer).executeAgentWithWorker(
                    properties(5000),
                    new AgentDefinitionSnapshot(),
                    command(),
                    frame -> { },
                    () -> false,
                    "/bin/sh",
                    worker,
                    "",
                    Map.of()
            );

            assertThat(issuedRunId).hasValue("run-test");
            assertThat(result.path("platformToken").asText()).isEqualTo("run-bound-token");
        } finally {
            SystemContext.clearUserContext();
        }
    }

    @Test
    void generatesOneRunIdForTheWorkerPayloadAndTemporaryCredential() throws Exception {
        Path worker = shellWorker("payload=$(cat)\n"
                + "printf '%s\\n' \"{\\\"type\\\":\\\"result\\\",\\\"data\\\":{"
                + "\\\"payload\\\":$payload,"
                + "\\\"platformToken\\\":\\\"$AI_AGENT_PLATFORM_TOKEN\\\"}}\"\n");
        AtomicReference<String> issuedRunId = new AtomicReference<>();
        AgentTemporaryTokenIssuer issuer = new AgentTemporaryTokenIssuer() {
            @Override
            public String issue(UserContext userContext) {
                return "legacy-token";
            }

            @Override
            public String issue(UserContext userContext, String agentRunId) {
                issuedRunId.set(agentRunId);
                return "generated-run-token";
            }
        };
        MutableUserContext userContext = new MutableUserContext();
        userContext.setSubject(new Subject(7L, "agent-user", "default", "USER"));
        SystemContext.setUserContext(userContext);
        AiAgentProperties properties = properties(5000);
        properties.setPythonCommand("/bin/sh");
        properties.setScriptPath(worker.toString());
        ProviderChatRequest request = new ProviderChatRequest();

        try {
            var result = new AiAgentProcessExecutor(objectMapper, issuer).execute(properties, request);

            String payloadRunId = result.path("payload").path("run").path("runId").asText();
            assertThat(payloadRunId).matches("agent-run-[0-9a-f]{32}");
            assertThat(issuedRunId).hasValue(payloadRunId);
            assertThat(result.path("platformToken").asText()).isEqualTo("generated-run-token");
        } finally {
            SystemContext.clearUserContext();
        }
    }

    @Test
    void sendsTheDefaultConfidencePolicyToThePythonWorker() throws Exception {
        Path worker = shellWorker("payload=$(cat)\n"
                + "printf '%s\\n' \"{\\\"type\\\":\\\"result\\\",\\\"data\\\":$payload}\"\n");

        var result = new AiAgentProcessExecutor(objectMapper).executeAgentWithWorker(
                properties(5000),
                new AgentDefinitionSnapshot(),
                command(),
                frame -> { },
                () -> false,
                "/bin/sh",
                worker,
                "",
                Map.of()
        );

        assertThat(result.path("confidencePolicy").path("enabled").asBoolean()).isTrue();
        assertThat(result.path("confidencePolicy").path("threshold").asDouble()).isEqualTo(0.9d);
        assertThat(result.path("confidencePolicy").path("maxRetries").asInt()).isEqualTo(3);
        assertThat(result.path("confidencePolicy").path("retrieval").path("topK").asInt()).isEqualTo(5);
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
