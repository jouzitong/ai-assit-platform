package ai.platform.aiassit.conversation.protocol;

import ai.platform.aiassit.conversation.protocol.dto.ChatEventEnvelope;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatTransportProtocolAdapterTest {

    private final ChatTransportProtocolAdapter adapter = new ChatTransportProtocolAdapter();

    @Test
    void projectsConversationStartToFrontendInitializationSequence() {
        ConversationQueryStreamEvent event = event("progress", "3");
        event.setSource("CONVERSATION");
        event.setPhase("STARTED");
        event.setSessionCode("session-1");
        event.setSessionName("会话标题");
        event.setRoundCode("round-1");
        event.setExt(new LinkedHashMap<>(Map.of(
                "userMessage", Map.of("id", "message-1", "role", "user")
        )));

        List<ChatEventEnvelope> result = adapter.adapt(event);

        assertThat(result).extracting(ChatEventEnvelope::getEventType)
                .containsExactly("session.initialized", "round.initialized", "assistant.started", "thinking.started");
        assertThat(result).extracting(ChatEventEnvelope::getEventId)
                .containsExactly("3.1", "3.2", "3.3", "3.4");
        assertThat(result).allSatisfy(item -> {
            assertThat(item.getSchemaVersion()).isEqualTo("chat-event.v2");
            assertThat(item.getSessionCode()).isEqualTo("session-1");
            assertThat(item.getRoundCode()).isEqualTo("round-1");
        });
    }

    @Test
    void projectsRenderAnswerToArtifactReferenceAndMessage() {
        ConversationQueryStreamEvent event = event("answer", "8");
        event.setSource("RENDER");
        event.setAnswer("{\"component\":\"chart\"}");
        event.setStatus("SUCCESS");
        event.setExt(new LinkedHashMap<>(Map.of(
                "codeRef", "artifact-1",
                "artifactType", "MODEL_RESPONSE_SNAPSHOT",
                "contentFormat", "JSON",
                "title", "Render JSON"
        )));

        List<ChatEventEnvelope> result = adapter.adapt(event);

        assertThat(result).extracting(ChatEventEnvelope::getEventType)
                .containsExactly("artifacts.build", "assistant.message.delta");
        assertThat(result.get(0).getPayload().toString()).contains("artifact-1");
        assertThat(result.get(1).getPayload().toString()).contains("component");
    }

    @Test
    void projectsAnswerDeltaToAssistantMessageDelta() {
        ConversationQueryStreamEvent event = event("answer_delta", "7");
        event.setDelta("正在生成");
        event.setStatus("RUNNING");

        ChatEventEnvelope result = adapter.adapt(event).get(0);

        assertThat(result.getEventType()).isEqualTo("assistant.message.delta");
        assertThat(result.getPayload().toString()).contains("正在生成", "append=true");
    }

    @Test
    void suppressesProviderRoundTerminalEvents() {
        assertThat(adapter.adapt(event("round.completed", "8.1"))).isEmpty();
        assertThat(adapter.adapt(event("round.failed", "8.2"))).isEmpty();
        assertThat(adapter.adapt(event("round.cancelled", "8.3"))).isEmpty();
    }

    @Test
    void projectsCompleteToThinkingAndRoundCompletion() {
        ConversationQueryStreamEvent event = event("complete", "12");
        event.setStatus("SUCCESS");
        event.setAnswer("done");

        assertThat(adapter.adapt(event)).extracting(ChatEventEnvelope::getEventType)
                .containsExactly("thinking.completed", "round.completed");
    }

    @Test
    void projectsAgentActivityToStructuredThinkingUpdate() {
        ConversationQueryStreamEvent event = event("progress", "9");
        event.setProgressType("ACTIVITY");
        event.setSource("AI_AGENT");
        event.setPhase("RUNNING");
        event.setStatus("RUNNING");
        event.setMessage("AI Agent 正在调用工具");
        event.setExt(new LinkedHashMap<>(Map.of(
                "agentCode", "render-agent",
                "activity", "tool_called",
                "activityType", "TOOL_CALL",
                "toolName", "render_json_validate_tool",
                "callId", "call-1"
        )));

        ChatEventEnvelope result = adapter.adapt(event).get(0);

        assertThat(result.getEventType()).isEqualTo("thinking.updated");
        assertThat(result.getPayload().get("action")).isEqualTo("activity.updated");
        assertThat(result.getPayload().get("progressType")).isEqualTo("ACTIVITY");
        assertThat(result.getPayload().get("activity").toString())
                .contains("call-1", "TOOL_CALL", "render_json_validate_tool");
    }

    @Test
    void projectsFailureWithExplicitChineseMessageAndCompatibleRoundMessage() {
        ConversationQueryStreamEvent event = event("error", "10");
        event.setRoundCode("round-1");
        event.setMessage("internal provider stack detail");
        event.setExt(new LinkedHashMap<>(Map.of(
                "errorCode", "PROVIDER_UNAVAILABLE",
                "userMessage", "模型服务暂时不可用",
                "retryable", false,
                "traceId", "trace-from-extension"
        )));

        ChatEventEnvelope result = adapter.adapt(event).get(0);
        Map<String, Object> error = error(result);

        assertThat(result.getEventType()).isEqualTo("round.failed");
        assertThat(result.getPayload().get("round").toString())
                .contains("internal provider stack detail");
        assertThat(error)
                .containsEntry("code", "PROVIDER_UNAVAILABLE")
                .containsEntry("userMessage", "模型服务暂时不可用")
                .containsEntry("retryable", false)
                .containsEntry("traceId", "trace-from-extension")
                .containsEntry("detail", "internal provider stack detail");
    }

    @Test
    void mapsCredentialFailureAndRedactsSensitiveDetail() {
        ConversationQueryStreamEvent event = event("error", "11");
        event.setSource("AI_AGENT");
        event.setMessage("invalid API key; Authorization: Bearer sk-liveSecret123 apiKey=plain-key secret:top-secret token=token-value");

        Map<String, Object> error = error(adapter.adapt(event).get(0));

        assertThat(error)
                .containsEntry("code", "MODEL_CREDENTIAL_INVALID")
                .containsEntry("userMessage", "模型服务凭证无效，请联系管理员检查配置")
                .containsEntry("retryable", false)
                .containsEntry("traceId", "request-1");
        assertThat((String) error.get("detail"))
                .contains("Authorization: ***", "apiKey=***", "secret:***", "token=***")
                .doesNotContain("sk-liveSecret123", "plain-key", "top-secret", "token-value");
    }

    @Test
    void mapsEnglishExtensionMessageToChineseUserMessage() {
        ConversationQueryStreamEvent event = event("error", "11.1");
        event.setMessage("provider request failed");
        event.setExt(new LinkedHashMap<>(Map.of(
                "userMessage", "Invalid API key supplied"
        )));

        Map<String, Object> error = error(adapter.adapt(event).get(0));

        assertThat(error)
                .containsEntry("code", "MODEL_CREDENTIAL_INVALID")
                .containsEntry("userMessage", "模型服务凭证无效，请联系管理员检查配置")
                .containsEntry("retryable", false)
                .containsEntry("detail", "provider request failed");
    }

    @Test
    void mapsModelConfigurationAndModelAvailabilityAsNotRetryable() {
        ConversationQueryStreamEvent missingConfig = event("error", "12");
        missingConfig.setMessage("required API key is missing");
        ConversationQueryStreamEvent missingModel = event("error", "13");
        missingModel.setMessage("model not found: retired-model");

        assertThat(error(adapter.adapt(missingConfig).get(0)))
                .containsEntry("code", "MODEL_CONFIG_INVALID")
                .containsEntry("retryable", false);
        assertThat(error(adapter.adapt(missingModel).get(0)))
                .containsEntry("code", "MODEL_NOT_AVAILABLE")
                .containsEntry("userMessage", "当前模型不可用，请联系管理员检查配置")
                .containsEntry("retryable", false);
    }

    @Test
    void mapsAgentTimeoutConnectionAndWorkflowFailuresAsRetryable() {
        ConversationQueryStreamEvent agentTimeout = event("error", "14");
        agentTimeout.setSource("AI_AGENT");
        agentTimeout.setMessage("python process timeout");
        ConversationQueryStreamEvent connection = event("error", "15");
        connection.setMessage("connection reset by peer");
        ConversationQueryStreamEvent workflow = event("error", "16");
        workflow.setSource("CONVERSATION");
        workflow.setMessage("workflow execution failed");

        assertThat(error(adapter.adapt(agentTimeout).get(0)))
                .containsEntry("code", "AI_AGENT_TIMEOUT")
                .containsEntry("userMessage", "AI Agent 执行超时，请稍后重试")
                .containsEntry("retryable", true);
        assertThat(error(adapter.adapt(connection).get(0)))
                .containsEntry("code", "MODEL_CONNECTION_FAILED")
                .containsEntry("retryable", true);
        assertThat(error(adapter.adapt(workflow).get(0)))
                .containsEntry("code", "WORKFLOW_EXECUTION_FAILED")
                .containsEntry("retryable", true);
    }

    @Test
    void limitsSanitizedErrorDetailAndAlwaysOutputsStableFields() {
        ConversationQueryStreamEvent event = event("error", "17");
        event.setRequestId(null);
        event.setMessage("Bearer sk-secretLong123 token=hidden " + "x".repeat(700));

        Map<String, Object> error = error(adapter.adapt(event).get(0));

        assertThat(error).containsKeys("code", "userMessage", "retryable", "traceId", "detail");
        assertThat(error.get("traceId")).isEqualTo("");
        assertThat((String) error.get("detail"))
                .hasSize(500)
                .endsWith("…")
                .doesNotContain("sk-secretLong123", "token=hidden");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> error(ChatEventEnvelope envelope) {
        return (Map<String, Object>) envelope.getPayload().get("error");
    }

    private ConversationQueryStreamEvent event(String type, String id) {
        ConversationQueryStreamEvent event = new ConversationQueryStreamEvent();
        event.setRunId("run-1");
        event.setRequestId("request-1");
        event.setEventId(id);
        event.setTimestamp(1_700_000_000_000L);
        event.setEventType(type);
        return event;
    }
}
