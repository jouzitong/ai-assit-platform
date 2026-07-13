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
                "nodeCode", "render",
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
    void projectsFailureWithSafeErrorAndCompatibleRoundMessage() {
        ConversationQueryStreamEvent event = event("error", "10");
        event.setRoundCode("round-1");
        event.setMessage("internal provider stack detail");
        event.setExt(new LinkedHashMap<>(Map.of(
                "errorCode", "PROVIDER_UNAVAILABLE",
                "userMessage", "模型服务暂时不可用",
                "retryable", true
        )));

        ChatEventEnvelope result = adapter.adapt(event).get(0);

        assertThat(result.getEventType()).isEqualTo("round.failed");
        assertThat(result.getPayload().get("round").toString())
                .contains("internal provider stack detail");
        assertThat(result.getPayload().get("error").toString())
                .contains("PROVIDER_UNAVAILABLE", "模型服务暂时不可用", "retryable=true", "request-1");
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
