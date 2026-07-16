package ai.platform.aiassit.service.ai.agent.service;

import ai.platform.aiassit.service.ai.agent.config.AiAgentProperties;
import ai.platform.aiassit.service.ai.spi.agent.AgentCancellation;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunCommand;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunEvent;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunResult;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAgentTypescriptRuntimeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void declaresThePinnedTypescriptCapabilitiesAndNormalizesEvents() throws Exception {
        AiAgentTypescriptRuntime runtime = runtime(new SuccessfulExecutor(objectMapper));

        assertThat(runtime.capabilities().getRuntimeType())
                .isEqualTo(AgentRuntimeType.OPENAI_AGENTS_TYPESCRIPT);
        assertThat(runtime.capabilities().getSdkVersion()).isEqualTo("0.13.4");

        AgentRunEvent event = runtime.toEvent(objectMapper.readTree("""
                {
                  "type":"event",
                  "eventType":"tool.completed",
                  "runId":"run-1",
                  "agentCode":"reviewer",
                  "agentVersion":2,
                  "status":"SUCCESS",
                  "ext":{"toolCode":"validator","toolVersion":4}
                }
                """));

        assertThat(event.getEventType()).isEqualTo("tool.completed");
        assertThat(event.getAgentCode()).isEqualTo("reviewer");
        assertThat(event.getExt()).containsEntry("toolCode", "validator");
        assertThat(event.getExt()).containsEntry("toolVersion", 4L);
    }

    @Test
    void runsTheTypescriptContractAndReturnsNormalizedArtifacts() {
        AiAgentTypescriptRuntime runtime = runtime(new SuccessfulExecutor(objectMapper));
        AgentRunCommand command = new AgentRunCommand();
        command.setRunId("run-1");
        List<AgentRunEvent> events = new ArrayList<>();

        AgentRunResult result = runtime.run(
                new AgentDefinitionSnapshot(),
                command,
                events::add,
                AgentCancellation.NONE
        );

        assertThat(events).extracting(AgentRunEvent::getEventType)
                .containsExactly("agent.started", "artifact.created", "round.completed");
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getFinalOutput()).isEqualTo("done");
        assertThat(result.getArtifacts()).singleElement()
                .satisfies(artifact -> assertThat(artifact)
                        .containsEntry("artifactCode", "checked")
                        .containsEntry("contentFormat", "JSON"));
    }

    @Test
    void emitsOneCancelledTerminalEventWhenTheWorkerIsCancelled() {
        AiAgentTypescriptRuntime runtime = runtime(new FailingExecutor(
                objectMapper,
                new IllegalStateException("agent run cancelled")
        ));
        AgentRunCommand command = new AgentRunCommand();
        command.setRunId("run-cancelled");
        List<AgentRunEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> runtime.run(
                new AgentDefinitionSnapshot(),
                command,
                events::add,
                () -> true
        )).isInstanceOf(IllegalStateException.class);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo("round.cancelled");
            assertThat(event.getStatus()).isEqualTo("CANCELLED");
        });
    }

    private AiAgentTypescriptRuntime runtime(AiAgentTypescriptProcessExecutor executor) {
        return new AiAgentTypescriptRuntime(new AiAgentProperties(), executor, objectMapper);
    }

    private static final class SuccessfulExecutor extends AiAgentTypescriptProcessExecutor {
        private final ObjectMapper objectMapper;

        private SuccessfulExecutor(ObjectMapper objectMapper) {
            super(new AiAgentProcessExecutor(objectMapper));
            this.objectMapper = objectMapper;
        }

        @Override
        public JsonNode executeAgent(AiAgentProperties properties,
                                     AgentDefinitionSnapshot snapshot,
                                     AgentRunCommand command,
                                     Consumer<JsonNode> frameConsumer,
                                     AgentCancellation cancellation) {
            for (Map<String, Object> frame : List.of(
                    Map.<String, Object>of("type", "event", "eventType", "agent.started", "status", "RUNNING"),
                    Map.<String, Object>of("type", "event", "eventType", "artifact.created", "status", "SUCCESS"),
                    Map.<String, Object>of("type", "event", "eventType", "round.completed", "status", "SUCCESS")
            )) {
                frameConsumer.accept(objectMapper.valueToTree(frame));
            }
            return objectMapper.valueToTree(Map.of(
                    "runId", "run-1",
                    "status", "SUCCESS",
                    "finalOutput", "done",
                    "finalAgentCode", "root",
                    "artifacts", List.of(Map.of(
                            "artifactCode", "checked",
                            "artifactType", "CHECK_RESULT",
                            "contentFormat", "JSON",
                            "content", Map.of("valid", true)
                    )),
                    "usage", Map.of("inputTokens", 3, "outputTokens", 2, "totalTokens", 5)
            ));
        }
    }

    private static final class FailingExecutor extends AiAgentTypescriptProcessExecutor {
        private final RuntimeException failure;

        private FailingExecutor(ObjectMapper objectMapper, RuntimeException failure) {
            super(new AiAgentProcessExecutor(objectMapper));
            this.failure = failure;
        }

        @Override
        public JsonNode executeAgent(AiAgentProperties properties,
                                     AgentDefinitionSnapshot snapshot,
                                     AgentRunCommand command,
                                     Consumer<JsonNode> frameConsumer,
                                     AgentCancellation cancellation) {
            throw failure;
        }
    }
}
