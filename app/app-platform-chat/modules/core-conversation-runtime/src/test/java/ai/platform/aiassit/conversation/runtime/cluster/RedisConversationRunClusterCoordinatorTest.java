package ai.platform.aiassit.conversation.runtime.cluster;

import ai.platform.aiassit.conversation.runtime.config.ConversationRuntimeProperties;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunState;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisConversationRunClusterCoordinatorTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ListOperations<String, String> lists;
    private ZSetOperations<String, String> sortedSets;
    private ObjectMapper objectMapper;
    private ConversationRuntimeProperties properties;
    private RedisConversationRunClusterCoordinator coordinator;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        lists = mock(ListOperations.class);
        sortedSets = mock(ZSetOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForList()).thenReturn(lists);
        when(redis.opsForZSet()).thenReturn(sortedSets);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        properties = new ConversationRuntimeProperties();
        properties.setNodeId("node-a");
        coordinator = new RedisConversationRunClusterCoordinator(
                redis,
                objectMapper,
                mock(RedisMessageListenerContainer.class),
                properties
        );
    }

    @Test
    void storesAndReadsRunSnapshot() throws Exception {
        ConversationRunSnapshot snapshot = snapshot(ConversationRunState.RUNNING);
        when(values.get("ai:chat:runtime:run:run-1"))
                .thenReturn(objectMapper.writeValueAsString(snapshot));

        coordinator.save(snapshot);

        verify(values).set(eq("ai:chat:runtime:run:run-1"), any(String.class), eq(properties.getActiveTtl()));
        assertThat(coordinator.find("run-1", null, null, 7L)).contains(snapshot);
    }

    @Test
    void storesAndBroadcastsReplayableEvent() {
        ConversationRunSnapshot snapshot = snapshot(ConversationRunState.RUNNING);
        ConversationQueryStreamEvent event = new ConversationQueryStreamEvent();
        event.setRunId("run-1");
        event.setEventId("3");
        event.setEventType("progress");

        coordinator.publish(snapshot, event);

        verify(lists).rightPush(eq("ai:chat:runtime:events:run-1"), any(String.class));
        verify(lists).trim("ai:chat:runtime:events:run-1", -512, -1);
        verify(redis).convertAndSend(eq("ai:chat:runtime:channel:events"), any(String.class));
    }

    @Test
    void persistsCancellationFlagBeforeBroadcasting() {
        ConversationRunSnapshot snapshot = snapshot(ConversationRunState.RUNNING);

        assertThat(coordinator.requestCancel(snapshot)).isTrue();

        verify(values).set(
                "ai:chat:runtime:cancel:run-1",
                "1",
                properties.getActiveTtl()
        );
        verify(redis).convertAndSend("ai:chat:runtime:channel:cancel", "run-1");
    }

    @Test
    void replaysOnlyEventsAfterLastEventId() throws Exception {
        ConversationRunSnapshot snapshot = snapshot(ConversationRunState.RUNNING);
        when(values.get("ai:chat:runtime:run:run-1"))
                .thenReturn(objectMapper.writeValueAsString(snapshot));
        ConversationQueryStreamEvent first = event("1", "progress");
        ConversationQueryStreamEvent second = event("2", "answer_delta");
        when(lists.range("ai:chat:runtime:events:run-1", 0, -1)).thenReturn(List.of(
                objectMapper.writeValueAsString(first),
                objectMapper.writeValueAsString(second)
        ));
        List<ConversationQueryStreamEvent> replay = new CopyOnWriteArrayList<>();

        coordinator.subscribe("run-1", 7L, "1.2", replay::add);

        assertThat(replay).extracting(ConversationQueryStreamEvent::getEventId).containsExactly("2");
    }

    private ConversationQueryStreamEvent event(String eventId, String eventType) {
        ConversationQueryStreamEvent event = new ConversationQueryStreamEvent();
        event.setRunId("run-1");
        event.setEventId(eventId);
        event.setEventType(eventType);
        return event;
    }

    private ConversationRunSnapshot snapshot(ConversationRunState state) {
        Instant now = Instant.parse("2026-07-11T12:00:00Z");
        return new ConversationRunSnapshot(
                "run-1",
                "node-a",
                "trace-1",
                7L,
                "session-1",
                "round-1",
                state,
                now,
                now,
                null,
                null
        );
    }
}
