package ai.platform.aiassit.conversation.runtime.impl;

import ai.platform.aiassit.conversation.dto.chat.ConversationQueryResponse;
import ai.platform.aiassit.conversation.dto.chat.ConversationStreamReconnectRequest;
import ai.platform.aiassit.conversation.runtime.cluster.ConversationRunClusterCoordinator;
import ai.platform.aiassit.conversation.runtime.cluster.LocalConversationRunClusterCoordinator;
import ai.platform.aiassit.conversation.runtime.config.ConversationRuntimeProperties;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscriber;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscription;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunState;
import ai.platform.aiassit.conversation.service.ConversationExecutionService;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationCancellation;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultConversationRunManagerTest {

    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void publishesVersionedReplayableEvents() throws Exception {
        ConversationExecutionService service = new StubExecutionService() {
            @Override
            public ConversationQueryResponse executeStream(ConversationQueryCommand command,
                                                           ConversationEventPublisher publisher,
                                                           ConversationCancellation cancellation) {
                ConversationQueryStreamEvent progress = event("progress", "RUNNING");
                progress.setSessionCode("session-1");
                progress.setRoundCode("round-1");
                publisher.publish(progress);
                publisher.publish(event("complete", "SUCCESS"));
                return new ConversationQueryResponse();
            }
        };
        DefaultConversationRunManager manager = manager(service);
        ConversationRunSnapshot started = manager.start(command());
        ConversationRunSnapshot completed = awaitState(manager, started.runId(), ConversationRunState.COMPLETED);

        List<ConversationQueryStreamEvent> replay = new CopyOnWriteArrayList<>();
        manager.subscribe(started.runId(), 7L, "2", replay::add);

        assertEquals("session-1", completed.sessionCode());
        assertEquals("round-1", completed.roundCode());
        assertFalse(replay.isEmpty());
        assertTrue(replay.stream().allMatch(event -> "1.0".equals(event.getProtocolVersion())));
        assertTrue(replay.stream().allMatch(event -> started.runId().equals(event.getRunId())));
        assertTrue(replay.stream().allMatch(event -> Long.parseLong(event.getEventId()) > 2L));
        assertTrue(replay.stream().anyMatch(event -> "complete".equals(event.getEventType())));
    }

    @Test
    void cancelsAnActiveRunIdempotently() throws Exception {
        CountDownLatch executing = new CountDownLatch(1);
        ConversationExecutionService service = new StubExecutionService() {
            @Override
            public ConversationQueryResponse executeStream(ConversationQueryCommand command,
                                                           ConversationEventPublisher publisher,
                                                           ConversationCancellation cancellation) {
                executing.countDown();
                while (true) {
                    cancellation.throwIfCancellationRequested();
                    try {
                        Thread.sleep(10L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
        DefaultConversationRunManager manager = manager(service);
        ConversationRunSnapshot run = manager.start(command());
        assertTrue(executing.await(2, TimeUnit.SECONDS));

        assertTrue(manager.cancel(run.runId(), null, null, 7L));
        assertTrue(manager.cancel(run.runId(), null, null, 7L));
        ConversationRunSnapshot cancelled = awaitState(manager, run.runId(), ConversationRunState.CANCELLED);

        List<ConversationQueryStreamEvent> replay = new CopyOnWriteArrayList<>();
        manager.subscribe(run.runId(), 7L, null, replay::add);
        assertFalse(cancelled.active());
        assertTrue(replay.stream().anyMatch(event -> "run.cancelled".equals(event.getEventType())));
    }

    @Test
    void delegatesRemoteLookupSubscriptionAndCancellationToClusterCoordinator() {
        ConversationRunSnapshot remote = new ConversationRunSnapshot(
                "run-remote",
                "node-b",
                "trace-remote",
                7L,
                "session-remote",
                "round-remote",
                ConversationRunState.RUNNING,
                Instant.now(),
                Instant.now(),
                null,
                null
        );
        StubClusterCoordinator coordinator = new StubClusterCoordinator(remote);
        DefaultConversationRunManager manager = manager(new StubExecutionService() {
            @Override
            public ConversationQueryResponse executeStream(ConversationQueryCommand command,
                                                           ConversationEventPublisher publisher,
                                                           ConversationCancellation cancellation) {
                return new ConversationQueryResponse();
            }
        }, coordinator);

        assertEquals(remote, manager.find("run-remote", null, null, 7L).orElseThrow());
        List<ConversationQueryStreamEvent> events = new CopyOnWriteArrayList<>();
        manager.subscribe("run-remote", 7L, null, events::add);
        assertTrue(coordinator.subscribed);
        assertTrue(manager.cancel("run-remote", null, null, 7L));
        assertTrue(coordinator.cancelRequested);
    }

    private DefaultConversationRunManager manager(ConversationExecutionService service) {
        ConversationRuntimeProperties properties = new ConversationRuntimeProperties();
        properties.setNodeId("test-node");
        return manager(service, new LocalConversationRunClusterCoordinator(properties.resolvedNodeId()), properties);
    }

    private DefaultConversationRunManager manager(ConversationExecutionService service,
                                                  ConversationRunClusterCoordinator coordinator) {
        ConversationRuntimeProperties properties = new ConversationRuntimeProperties();
        properties.setNodeId(coordinator.nodeId());
        return manager(service, coordinator, properties);
    }

    private DefaultConversationRunManager manager(ConversationExecutionService service,
                                                  ConversationRunClusterCoordinator coordinator,
                                                  ConversationRuntimeProperties properties) {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(8);
        executor.initialize();
        return new DefaultConversationRunManager(service, executor, coordinator, properties);
    }

    private ConversationQueryCommand command() {
        ConversationQueryCommand command = new ConversationQueryCommand();
        command.setTraceId("trace-1");
        command.setUserId(7L);
        command.setMessage("hello");
        return command;
    }

    private ConversationQueryStreamEvent event(String type, String status) {
        ConversationQueryStreamEvent event = new ConversationQueryStreamEvent();
        event.setEventType(type);
        event.setStatus(status);
        return event;
    }

    private ConversationRunSnapshot awaitState(DefaultConversationRunManager manager,
                                               String runId,
                                               ConversationRunState expected) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(3));
        while (Instant.now().isBefore(deadline)) {
            ConversationRunSnapshot snapshot = manager.find(runId, null, null, 7L).orElseThrow();
            if (snapshot.state() == expected) {
                return snapshot;
            }
            Thread.sleep(10L);
        }
        ConversationRunSnapshot snapshot = manager.find(runId, null, null, 7L).orElseThrow();
        assertEquals(expected, snapshot.state());
        return snapshot;
    }

    private abstract static class StubExecutionService implements ConversationExecutionService {

        @Override
        public ConversationQueryResponse execute(ConversationQueryCommand command) {
            return new ConversationQueryResponse();
        }

        @Override
        public List<ConversationQueryStreamEvent> replayStream(ConversationStreamReconnectRequest request,
                                                               Long userId,
                                                               String traceId) {
            return List.of();
        }
    }

    private static final class StubClusterCoordinator implements ConversationRunClusterCoordinator {

        private final ConversationRunSnapshot remote;
        private boolean subscribed;
        private boolean cancelRequested;

        private StubClusterCoordinator(ConversationRunSnapshot remote) {
            this.remote = remote;
        }

        @Override
        public String nodeId() {
            return "node-a";
        }

        @Override
        public boolean distributed() {
            return true;
        }

        @Override
        public void registerLocalCancelHandler(java.util.function.Consumer<String> cancelHandler) {
        }

        @Override
        public void save(ConversationRunSnapshot snapshot) {
        }

        @Override
        public void publish(ConversationRunSnapshot snapshot, ConversationQueryStreamEvent event) {
        }

        @Override
        public Optional<ConversationRunSnapshot> find(String runId,
                                                      String sessionCode,
                                                      String roundCode,
                                                      Long userId) {
            return "run-remote".equals(runId) && Long.valueOf(7L).equals(userId)
                    ? Optional.of(remote)
                    : Optional.empty();
        }

        @Override
        public ConversationRunSubscription subscribe(String runId,
                                                     Long userId,
                                                     String lastEventId,
                                                     ConversationRunSubscriber subscriber) {
            subscribed = true;
            return () -> { };
        }

        @Override
        public boolean requestCancel(ConversationRunSnapshot snapshot) {
            cancelRequested = true;
            return true;
        }

        @Override
        public boolean isCancelRequested(String runId) {
            return false;
        }
    }
}
