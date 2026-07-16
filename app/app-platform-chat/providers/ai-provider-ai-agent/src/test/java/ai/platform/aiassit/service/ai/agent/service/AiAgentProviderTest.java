package ai.platform.aiassit.service.ai.agent.service;

import ai.platform.aiassit.service.ai.agent.config.AiAgentProperties;
import ai.platform.aiassit.service.ai.api.dto.ChatMessage;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.enums.MessageRole;
import ai.platform.aiassit.service.ai.api.stream.ChatChunk;
import ai.platform.aiassit.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassit.service.ai.spi.agent.AgentCancellation;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunCommand;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunEvent;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunResult;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeType;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class AiAgentProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void emitsFallbackFailureActivityWhenProcessFailsBeforePythonErrorFrame() {
        AiAgentProperties properties = new AiAgentProperties();
        ProviderChatRequest request = new ProviderChatRequest();
        IllegalStateException failure = new IllegalStateException("python process timeout");
        AiAgentProcessExecutor processExecutor = new FailingProcessExecutor(objectMapper, failure);
        CapturingObserver observer = new CapturingObserver();

        new AiAgentProvider(properties, processExecutor, objectMapper).chatStream(request, observer);

        assertThat(observer.chunks).hasSize(1);
        ChatChunk chunk = observer.chunks.get(0);
        assertThat(chunk.getProgressType()).isEqualTo("ACTIVITY");
        assertThat(chunk.getPhase()).isEqualTo("FAILED");
        assertThat(chunk.getStatus()).isEqualTo("FAILED");
        assertThat(chunk.getMessage()).isEqualTo("AI Agent 执行超时");
        assertThat(chunk.getExt()).containsEntry("activityType", "AI_AGENT_EXECUTION");
        assertThat(observer.error).isSameAs(failure);
    }

    @Test
    void mapsProtocolV2FramesToTheLanguageNeutralRuntimeEvent() throws Exception {
        AiAgentProvider provider = new AiAgentProvider(
                new AiAgentProperties(),
                new AiAgentProcessExecutor(objectMapper),
                objectMapper
        );
        JsonNode frame = objectMapper.readTree("""
                {
                  "protocolVersion":"2.0",
                  "type":"event",
                  "eventType":"tool.completed",
                  "runId":"run-1",
                  "requestId":"request-1",
                  "traceId":"trace-1",
                  "agentCode":"reviewer",
                  "agentVersion":3,
                  "agentName":"Reviewer",
                  "status":"SUCCESS",
                  "timestamp":"2026-07-16T08:00:00Z",
                  "ext":{"toolCode":"validator","callId":"call-1"}
                }
                """);

        AgentRunEvent event = provider.toAgentRunEvent(frame);

        assertThat(event.getEventType()).isEqualTo("tool.completed");
        assertThat(event.getRunId()).isEqualTo("run-1");
        assertThat(event.getAgentCode()).isEqualTo("reviewer");
        assertThat(event.getAgentVersion()).isEqualTo(3);
        assertThat(event.getExt()).containsEntry("toolCode", "validator");
        assertThat(event.getExt()).containsEntry("callId", "call-1");
    }

    @Test
    void declaresThePinnedPythonAgentRuntimeCapabilities() {
        AiAgentProvider provider = new AiAgentProvider(
                new AiAgentProperties(),
                new AiAgentProcessExecutor(objectMapper),
                objectMapper
        );

        assertThat(provider.capabilities().getRuntimeType())
                .isEqualTo(AgentRuntimeType.OPENAI_AGENTS_PYTHON);
        assertThat(provider.capabilities().getSdkVersion()).isEqualTo("0.18.2");
        assertThat(provider.capabilities().getFeatures())
                .contains("agent-as-tool", "handoffs", "skill-on-demand", "actual-usage");
    }

    @Test
    void buildsProtocolV2PayloadWithoutSerializingCredentials() throws Exception {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setDefaultModel("fallback-model");
        ProviderChatRequest request = new ProviderChatRequest();
        request.setApiKey("secret-api-key");
        request.setModel("gpt-test");
        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent("current question");
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setRole(MessageRole.ASSISTANT);
        assistantMessage.setContent("previous answer");
        request.setMessages(List.of(assistantMessage, userMessage));
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("protocolVersion", "2.0");
        runtime.put("run", Map.of("runId", "run-1", "input", "hello"));
        runtime.put("rootAgent", Map.of("ref", "agent://root/v1"));
        runtime.put("agentGraph", List.of(Map.of("ref", "agent://root/v1")));
        runtime.put("resolvedCapabilities", Map.of("tools", List.of()));
        runtime.put("workflowSnapshot", Map.of("workflowCode", "check"));
        runtime.put("snapshotHash", "sha256:test");
        runtime.put("modelSettings", Map.of(
                "temperature", 0.3,
                "apiKey", "nested-secret",
                "unsupported", "ignored"
        ));
        request.getExt().put("agentRuntime", runtime);
        request.getExt().put("legacyFlag", true);
        request.getExt().put("accessToken", "legacy-token");
        request.getExt().put("toolGatewayToken", "gateway-secret");
        RequestMeta meta = new RequestMeta();
        meta.getExt().put("sessionToken", "meta-secret");
        request.setMeta(meta);

        Map<String, Object> payload = new AiAgentProcessExecutor(objectMapper).buildPayload(properties, request);
        String json = objectMapper.writeValueAsString(payload);

        assertThat(payload).containsEntry("protocolVersion", "2.0");
        assertThat(payload.get("run")).isEqualTo(runtime.get("run"));
        assertThat(payload.get("rootAgent")).isEqualTo(runtime.get("rootAgent"));
        assertThat(payload.get("resolvedCapabilities")).isEqualTo(runtime.get("resolvedCapabilities"));
        assertThat(payload.get("workflowSnapshot")).isEqualTo(runtime.get("workflowSnapshot"));
        assertThat(payload.get("ext")).isEqualTo(Map.of("legacyFlag", true));
        List<String> roles = ((List<?>) payload.get("messages")).stream()
                .map(item -> String.valueOf(((Map<?, ?>) item).get("role")))
                .toList();
        assertThat(roles).containsExactly("assistant", "user");
        Map<?, ?> options = (Map<?, ?>) payload.get("options");
        assertThat(options.get("temperature")).isEqualTo(0.3);
        assertThat(options.containsKey("apiKey")).isFalse();
        assertThat(options.containsKey("unsupported")).isFalse();
        assertThat(json).doesNotContain(
                "secret-api-key", "nested-secret", "legacy-token", "gateway-secret", "meta-secret",
                "apiKey", "agentRuntime"
        );
    }

    @Test
    void executesTheAgentRuntimeContractAndForwardsNormalizedEvents() {
        AiAgentProperties properties = new AiAgentProperties();
        AiAgentProvider provider = new AiAgentProvider(
                properties,
                new SuccessfulAgentProcessExecutor(objectMapper),
                objectMapper
        );
        AgentDefinitionSnapshot snapshot = new AgentDefinitionSnapshot();
        AgentRunCommand command = new AgentRunCommand();
        command.setRunId("run-1");
        command.setRequestId("request-1");
        List<AgentRunEvent> events = new ArrayList<>();

        AgentRunResult result = provider.run(
                snapshot,
                command,
                events::add,
                AgentCancellation.NONE
        );

        assertThat(events).extracting(AgentRunEvent::getEventType)
                .containsExactly("agent.started", "round.completed");
        assertThat(result.getRunId()).isEqualTo("run-1");
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getFinalOutput()).isEqualTo("done");
        assertThat(result.getFinalAgentCode()).isEqualTo("root");
        assertThat(result.getUsage().getTotalTokens()).isEqualTo(8);
    }

    private static final class FailingProcessExecutor extends AiAgentProcessExecutor {
        private final RuntimeException failure;

        private FailingProcessExecutor(ObjectMapper objectMapper, RuntimeException failure) {
            super(objectMapper);
            this.failure = failure;
        }

        @Override
        public JsonNode executeStream(AiAgentProperties properties,
                                      ProviderChatRequest request,
                                      Consumer<JsonNode> frameConsumer) {
            throw failure;
        }
    }

    private static final class SuccessfulAgentProcessExecutor extends AiAgentProcessExecutor {
        private final ObjectMapper objectMapper;

        private SuccessfulAgentProcessExecutor(ObjectMapper objectMapper) {
            super(objectMapper);
            this.objectMapper = objectMapper;
        }

        @Override
        public JsonNode executeAgent(AiAgentProperties properties,
                                     AgentDefinitionSnapshot snapshot,
                                     AgentRunCommand command,
                                     Consumer<JsonNode> frameConsumer,
                                     AgentCancellation cancellation) {
            frameConsumer.accept(objectMapper.valueToTree(Map.of(
                    "type", "event",
                    "eventType", "agent.started",
                    "runId", "run-1",
                    "requestId", "request-1",
                    "status", "RUNNING"
            )));
            frameConsumer.accept(objectMapper.valueToTree(Map.of(
                    "type", "event",
                    "eventType", "round.completed",
                    "runId", "run-1",
                    "requestId", "request-1",
                    "status", "SUCCESS"
            )));
            return objectMapper.valueToTree(Map.of(
                    "runId", "run-1",
                    "status", "SUCCESS",
                    "finalOutput", "done",
                    "finalAgentCode", "root",
                    "usage", Map.of("inputTokens", 5, "outputTokens", 3, "totalTokens", 8)
            ));
        }
    }

    private static final class CapturingObserver implements ChatStreamObserver {
        private final List<ChatChunk> chunks = new ArrayList<>();
        private Throwable error;

        @Override
        public void onChunk(ChatChunk chunk) {
            chunks.add(chunk);
        }

        @Override
        public void onComplete() {
            // Not expected in the failure case.
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }
    }
}
