package ai.platform.aiassit.conversation.transport.sse;

import ai.platform.aiassit.conversation.dto.chat.ConversationStreamReconnectRequest;
import ai.platform.aiassit.conversation.protocol.ChatProtocolEventCursor;
import ai.platform.aiassit.conversation.protocol.ChatTransportProtocolAdapter;
import ai.platform.aiassit.conversation.protocol.dto.ChatEventEnvelope;
import ai.platform.aiassit.conversation.protocol.dto.ChatTransportRequest;
import ai.platform.aiassit.conversation.runtime.ConversationRunManager;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscription;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunState;
import ai.platform.aiassit.conversation.service.ConversationExecutionService;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtocolSseConversationTransportTest {

    private final ConversationRunManager runManager = mock(ConversationRunManager.class);
    private final ConversationExecutionService executionService = mock(ConversationExecutionService.class);
    private final ChatTransportProtocolAdapter protocolAdapter = mock(ChatTransportProtocolAdapter.class);
    private final ChatProtocolEventCursor eventCursor = new ChatProtocolEventCursor();
    private final ProtocolSseConversationTransport transport = new ProtocolSseConversationTransport(
            runManager, executionService, protocolAdapter, eventCursor);

    @Test
    void reconnectsToOwnedRuntimeRunUsingProjectedEventCursor() {
        ChatTransportRequest request = request();
        request.setLastEventId("8.2");
        ConversationRunSnapshot run = new ConversationRunSnapshot(
                "run-1", "node-1", "request-1", 7L, "session-1", "round-1",
                ConversationRunState.RUNNING, Instant.now(), Instant.now(), null, null);
        when(runManager.find("run-1", "session-1", "round-1", 7L)).thenReturn(Optional.of(run));
        when(runManager.subscribe(eq("run-1"), eq(7L), eq("7"), any()))
                .thenReturn(mock(ConversationRunSubscription.class));

        transport.reconnect(request, 7L, "trace-1");

        verify(runManager).subscribe(eq("run-1"), eq(7L), eq("7"), any());
        verify(executionService, never()).replayStream(any(), any(), any());
    }

    @Test
    void replaysPersistedRoundForCurrentUserWhenRuntimeRunIsMissing() {
        ChatTransportRequest request = request();
        request.setLastEventId("8.2");
        when(runManager.find("run-1", "session-1", "round-1", 7L)).thenReturn(Optional.empty());

        ConversationQueryStreamEvent replayEvent = new ConversationQueryStreamEvent();
        replayEvent.setEventId("1");
        replayEvent.setEventType("answer");
        replayEvent.setAnswer("snapshot");
        when(executionService.replayStream(any(), eq(7L), eq("trace-1")))
                .thenReturn(List.of(replayEvent));

        ChatEventEnvelope envelope = new ChatEventEnvelope();
        envelope.setEventId("9");
        envelope.setEventType("assistant.message.delta");
        envelope.setPayload(Map.of());
        when(protocolAdapter.adapt(any())).thenReturn(List.of(envelope));

        transport.reconnect(request, 7L, "trace-1");

        ArgumentCaptor<ConversationStreamReconnectRequest> replayRequest =
                ArgumentCaptor.forClass(ConversationStreamReconnectRequest.class);
        verify(executionService).replayStream(replayRequest.capture(), eq(7L), eq("trace-1"));
        assertThat(replayRequest.getValue().getSessionCode()).isEqualTo("session-1");
        assertThat(replayRequest.getValue().getRoundCode()).isEqualTo("round-1");
        assertThat(replayRequest.getValue().getRunId()).isEqualTo("run-1");

        ArgumentCaptor<ConversationQueryStreamEvent> projectedEvent =
                ArgumentCaptor.forClass(ConversationQueryStreamEvent.class);
        verify(protocolAdapter).adapt(projectedEvent.capture());
        assertThat(projectedEvent.getValue().getRunId()).isEqualTo("run-1");
        assertThat(projectedEvent.getValue().getEventId()).isEqualTo("9");
        verify(runManager, never()).subscribe(any(), any(), any(), any());
    }

    private ChatTransportRequest request() {
        ChatTransportRequest request = new ChatTransportRequest();
        request.setRunId("run-1");
        request.setSessionCode("session-1");
        request.setRoundCode("round-1");
        return request;
    }
}
