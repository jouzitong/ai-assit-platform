package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.dto.protocol.RenderArtifactResponse;
import ai.platform.aiassit.conversation.dto.protocol.RoundThinkingResponse;
import ai.platform.aiassit.conversation.protocol.ChatTransportProtocolAdapter;
import ai.platform.aiassit.conversation.protocol.dto.ChatTransportRequest;
import ai.platform.aiassit.conversation.runtime.ConversationRunManager;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscriber;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscription;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunState;
import ai.platform.aiassit.conversation.service.ConversationProtocolQueryService;
import ai.platform.aiassit.conversation.support.ConversationCommandFactory;
import ai.platform.aiassit.conversation.support.ConversationRequestContextResolver;
import ai.platform.aiassit.conversation.transport.sse.ProtocolSseConversationTransport;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChatTransportProtocolControllerTest {

    private final StubTransport transport = new StubTransport();
    private final StubCommandFactory commandFactory = new StubCommandFactory();
    private final StubRequestContextResolver contextResolver = new StubRequestContextResolver();
    private final StubRunManager runManager = new StubRunManager();
    private final ChatTransportProtocolController controller = new ChatTransportProtocolController(
            transport,
            commandFactory,
            contextResolver,
            queryService(),
            runManager,
            new ChatTransportProtocolAdapter());

    @Test
    void usesLastEventIdHeaderWhenRequestBodyDoesNotProvideCursor() {
        ChatTransportRequest request = new ChatTransportRequest();

        SseEmitter result = controller.reconnect(request, "8.2");

        assertThat(result).isSameAs(transport.emitter);
        assertThat(request.getLastEventId()).isEqualTo("8.2");
        assertThat(transport.reconnectRequest).isSameAs(request);
        assertThat(transport.reconnectUserId).isEqualTo(7L);
        assertThat(transport.reconnectTraceId).isEqualTo("trace-1");
    }

    @Test
    void startsSettingsAssistantThroughDedicatedServerOwnedChannel() {
        ChatTransportRequest request = new ChatTransportRequest();
        ConversationQueryCommand command = new ConversationQueryCommand();
        commandFactory.settingsCommand = command;
        contextResolver.traceId = "trace-settings";

        SseEmitter result = controller.streamSettingsAssistant("settings-session", request);

        assertThat(result).isSameAs(transport.emitter);
        assertThat(commandFactory.settingsRequest).isSameAs(request);
        assertThat(commandFactory.settingsSessionCode).isEqualTo("settings-session");
        assertThat(commandFactory.settingsUserId).isEqualTo(7L);
        assertThat(commandFactory.settingsTraceId).isEqualTo("trace-settings");
        assertThat(commandFactory.settingsAllowModelOverride).isFalse();
        assertThat(transport.startedCommand).isSameAs(command);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsSanitizedErrorSummaryAndStructuredErrorInfoForRunRecovery() {
        ConversationRunSnapshot run = new ConversationRunSnapshot(
                "run-1", "node-1", "trace-1", 7L, "session-1", "round-1",
                ConversationRunState.FAILED, Instant.now(), Instant.now(), Instant.now(),
                "invalid API key Authorization: Bearer sk-secret123 token=raw-token");
        runManager.run = Optional.of(run);

        Map<String, Object> status = controller.runStatus("run-1");

        assertThat((String) status.get("error"))
                .doesNotContain("sk-secret123", "raw-token")
                .contains("Authorization: ***", "token=***");
        assertThat(status.get("errorInfo")).isInstanceOf(Map.class);
        Map<String, Object> errorInfo = (Map<String, Object>) status.get("errorInfo");
        assertThat(errorInfo)
                .containsEntry("code", "MODEL_CREDENTIAL_INVALID")
                .containsEntry("retryable", false)
                .containsEntry("traceId", "trace-1");
    }

    private ConversationProtocolQueryService queryService() {
        return new ConversationProtocolQueryService() {
            @Override
            public RoundThinkingResponse thinkingDetail(String sessionCode, String roundCode, Long userId) {
                return null;
            }

            @Override
            public RenderArtifactResponse renderArtifact(String codeRef, Long userId) {
                return null;
            }
        };
    }

    private static final class StubTransport extends ProtocolSseConversationTransport {
        private final SseEmitter emitter = new SseEmitter();
        private ConversationQueryCommand startedCommand;
        private ChatTransportRequest reconnectRequest;
        private Long reconnectUserId;
        private String reconnectTraceId;

        private StubTransport() {
            super(null, null, null, null);
        }

        @Override
        public SseEmitter start(ConversationQueryCommand command) {
            this.startedCommand = command;
            return emitter;
        }

        @Override
        public SseEmitter reconnect(ChatTransportRequest request, Long userId, String traceId) {
            this.reconnectRequest = request;
            this.reconnectUserId = userId;
            this.reconnectTraceId = traceId;
            return emitter;
        }
    }

    private static final class StubCommandFactory extends ConversationCommandFactory {
        private ConversationQueryCommand settingsCommand;
        private ChatTransportRequest settingsRequest;
        private String settingsSessionCode;
        private Long settingsUserId;
        private String settingsTraceId;
        private boolean settingsAllowModelOverride;

        private StubCommandFactory() {
            super(null);
        }

        @Override
        public ConversationQueryCommand fromSettingsAssistantProtocol(ChatTransportRequest request,
                                                                       String pathSessionCode,
                                                                       Long userId,
                                                                       String fallbackTraceId,
                                                                       boolean allowModelOverride) {
            this.settingsRequest = request;
            this.settingsSessionCode = pathSessionCode;
            this.settingsUserId = userId;
            this.settingsTraceId = fallbackTraceId;
            this.settingsAllowModelOverride = allowModelOverride;
            return settingsCommand;
        }
    }

    private static final class StubRequestContextResolver extends ConversationRequestContextResolver {
        private String traceId = "trace-1";

        @Override
        public Long currentUserId() {
            return 7L;
        }

        @Override
        public String traceId() {
            return traceId;
        }

        @Override
        public boolean canOverrideModel() {
            return false;
        }
    }

    private static final class StubRunManager implements ConversationRunManager {
        private Optional<ConversationRunSnapshot> run = Optional.empty();

        @Override
        public ConversationRunSnapshot start(ConversationQueryCommand command) {
            return null;
        }

        @Override
        public ConversationRunSubscription subscribe(String runId,
                                                     Long userId,
                                                     String lastEventId,
                                                     ConversationRunSubscriber subscriber) {
            return () -> { };
        }

        @Override
        public Optional<ConversationRunSnapshot> find(String runId,
                                                      String sessionCode,
                                                      String roundCode,
                                                      Long userId) {
            return run;
        }

        @Override
        public boolean cancel(String runId, String sessionCode, String roundCode, Long userId) {
            return false;
        }
    }
}
