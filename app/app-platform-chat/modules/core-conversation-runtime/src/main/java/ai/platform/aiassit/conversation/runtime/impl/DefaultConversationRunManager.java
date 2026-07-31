package ai.platform.aiassit.conversation.runtime.impl;

import ai.platform.aiassit.conversation.runtime.ConversationRunManager;
import ai.platform.aiassit.conversation.runtime.cluster.ConversationRunClusterCoordinator;
import ai.platform.aiassit.conversation.runtime.config.ConversationRuntimeProperties;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscriber;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscription;
import ai.platform.aiassit.conversation.runtime.task.ConversationCancellationToken;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunState;
import ai.platform.aiassit.conversation.service.ConversationExecutionService;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationCancelledException;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.thread.AsyncTaskExcutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class DefaultConversationRunManager implements ConversationRunManager {

    private final ConversationExecutionService executionService;
    private final AsyncTaskExcutor runExecutor;
    private final ConversationRunClusterCoordinator clusterCoordinator;
    private final ConversationRuntimeProperties properties;
    private final Map<String, RunEntry> runs = new ConcurrentHashMap<>();

    public DefaultConversationRunManager(
            ConversationExecutionService executionService,
            AsyncTaskExcutor runExecutor,
            ConversationRunClusterCoordinator clusterCoordinator,
            ConversationRuntimeProperties properties) {
        this.executionService = executionService;
        this.runExecutor = runExecutor;
        this.clusterCoordinator = clusterCoordinator;
        this.properties = properties;
        this.clusterCoordinator.registerLocalCancelHandler(this::cancelOwnedRun);
    }

    @Override
    public ConversationRunSnapshot start(ConversationQueryCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("conversation command is required");
        }
        cleanupExpiredRuns();
        String runId = "run_" + UUID.randomUUID().toString().replace("-", "");
        RunEntry entry = new RunEntry(
                runId,
                command,
                clusterCoordinator.nodeId(),
                () -> clusterCoordinator.isCancelRequested(runId)
        );
        runs.put(runId, entry);
        publish(entry, runtimeEvent("run.accepted", "ACCEPTED", "conversation run accepted"));
        entry.future = runExecutor.submit(() -> execute(entry, command));
        ConversationRunSnapshot snapshot = entry.snapshot();
        log.info("对话流任务已受理，run={}", snapshot);
        return snapshot;
    }

    @Override
    public ConversationRunSubscription subscribe(String runId,
                                                 Long userId,
                                                 String lastEventId,
                                                 ConversationRunSubscriber subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("run subscriber is required");
        }
        Optional<RunEntry> local = ownedRun(runId, userId);
        if (local.isEmpty()) {
            log.info("本机未找到对话流任务，转交集群订阅，runId={}, userId={}, lastEventId={}", runId, userId, lastEventId);
            return clusterCoordinator.subscribe(runId, userId, lastEventId, subscriber);
        }
        RunEntry entry = local.get();
        long lastSequence = parseSequence(lastEventId);
        boolean terminal;
        synchronized (entry) {
            for (ConversationQueryStreamEvent event : entry.events) {
                if (parseSequence(event.getEventId()) > lastSequence) {
                    deliver(entry, subscriber, event);
                }
            }
            terminal = entry.state.isTerminal();
            if (!terminal) {
                entry.subscribers.add(subscriber);
            }
        }
        if (terminal) {
            subscriber.onComplete();
        }
        log.info("对话流任务订阅处理完成，runId={}, userId={}, lastEventId={}, replayedEventCount={}, terminal={}",
                runId, userId, lastEventId, entry.events.size(), terminal);
        return () -> entry.subscribers.remove(subscriber);
    }

    @Override
    public Optional<ConversationRunSnapshot> find(String runId,
                                                  String sessionCode,
                                                  String roundCode,
                                                  Long userId) {
        cleanupExpiredRuns();
        Optional<ConversationRunSnapshot> local = findLocalEntry(runId, sessionCode, roundCode, userId)
                .map(RunEntry::snapshot);
        return local.isPresent()
                ? local
                : clusterCoordinator.find(runId, sessionCode, roundCode, userId);
    }

    @Override
    public boolean cancel(String runId, String sessionCode, String roundCode, Long userId) {
        Optional<RunEntry> local = findLocalEntry(runId, sessionCode, roundCode, userId);
        if (local.isPresent()) {
            return cancelEntry(local.get());
        }
        return clusterCoordinator.find(runId, sessionCode, roundCode, userId)
                .map(clusterCoordinator::requestCancel)
                .orElse(false);
    }

    private boolean cancelEntry(RunEntry entry) {
        synchronized (entry) {
            if (entry.state == ConversationRunState.CANCELLED) {
                return true;
            }
            if (entry.state.isTerminal()) {
                return false;
            }
            entry.state = ConversationRunState.CANCELLING;
            entry.cancellation.cancel();
            Future<?> future = entry.future;
            if (future != null) {
                future.cancel(true);
            }
            entry.state = ConversationRunState.CANCELLED;
            entry.finishedAt = Instant.now();
            publish(entry, runtimeEvent("run.cancelled", "CANCELLED", "conversation run cancelled"));
            log.info("对话流任务已取消，run={}", entry.snapshot());
        }
        completeSubscribers(entry);
        return true;
    }

    private void cancelOwnedRun(String runId) {
        RunEntry entry = runs.get(runId);
        if (entry != null) {
            cancelEntry(entry);
        }
    }

    private void execute(RunEntry entry, ConversationQueryCommand command) {
        long startedAt = System.currentTimeMillis();
        try {
            entry.cancellation.throwIfCancellationRequested();
            entry.state = ConversationRunState.RUNNING;
            entry.startedAt = Instant.now();
            publish(entry, runtimeEvent("run.started", "RUNNING", "conversation run started"));
            log.info("对话流任务开始执行，run={}", entry.snapshot());
            executionService.executeStream(command, event -> publish(entry, event), entry.cancellation);
            entry.cancellation.throwIfCancellationRequested();
            entry.state = ConversationRunState.COMPLETED;
            entry.finishedAt = Instant.now();
            log.info("对话流任务执行完成，run={}, durationMs={}", entry.snapshot(), System.currentTimeMillis() - startedAt);
        } catch (ConversationCancelledException ex) {
            markCancelled(entry);
            log.info("对话流任务执行被取消，run={}, durationMs={}", entry.snapshot(), System.currentTimeMillis() - startedAt);
        } catch (RuntimeException ex) {
            markFailed(entry, ex);
            log.error("对话流任务执行失败，run={}, durationMs={}", entry.snapshot(), System.currentTimeMillis() - startedAt, ex);
        } finally {
            clusterCoordinator.save(entry.snapshot());
            completeSubscribers(entry);
        }
    }

    private void markCancelled(RunEntry entry) {
        synchronized (entry) {
            if (entry.state == ConversationRunState.CANCELLED) {
                return;
            }
            entry.state = ConversationRunState.CANCELLED;
            entry.finishedAt = Instant.now();
            publish(entry, runtimeEvent("run.cancelled", "CANCELLED", "conversation run cancelled"));
        }
    }

    private void markFailed(RunEntry entry, RuntimeException ex) {
        synchronized (entry) {
            if (entry.state == ConversationRunState.CANCELLED) {
                return;
            }
            entry.state = ConversationRunState.FAILED;
            entry.finishedAt = Instant.now();
            entry.error = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
            if (!"error".equals(entry.lastEventType)) {
                publish(entry, runtimeEvent("error", "FAILED", entry.error));
            }
        }
    }

    private void publish(RunEntry entry, ConversationQueryStreamEvent event) {
        List<ConversationRunSubscriber> subscribers;
        synchronized (entry) {
            if (entry.state == ConversationRunState.CANCELLED
                    && !"run.cancelled".equals(event.getEventType())) {
                return;
            }
            long sequence = entry.eventSequence.incrementAndGet();
            event.setProtocolVersion("1.0");
            event.setRunId(entry.runId);
            event.setEventId(String.valueOf(sequence));
            event.setTimestamp(System.currentTimeMillis());
            if (!StringUtils.hasText(event.getRequestId())) {
                event.setRequestId(entry.requestId);
            }
            if (StringUtils.hasText(event.getSessionCode())) {
                entry.sessionCode = event.getSessionCode();
            }
            if (StringUtils.hasText(event.getRoundCode())) {
                entry.roundCode = event.getRoundCode();
            }
            entry.lastEventType = event.getEventType();
            entry.events.addLast(event);
            while (entry.events.size() > Math.max(1, properties.getMaxReplayEvents())) {
                entry.events.removeFirst();
            }
            subscribers = new ArrayList<>(entry.subscribers);
        }
        for (ConversationRunSubscriber subscriber : subscribers) {
            deliver(entry, subscriber, event);
        }
        logPublishedEvent(entry, event);
        clusterCoordinator.publish(entry.snapshot(), event);
    }

    private void logPublishedEvent(RunEntry entry, ConversationQueryStreamEvent event) {
        if ("answer.delta".equals(event.getEventType())) {
            log.debug("对话流回答增量已发布，event={}", event);
            return;
        }
        log.debug("对话流活动更新，event={}", event);
    }

    private void deliver(RunEntry entry,
                         ConversationRunSubscriber subscriber,
                         ConversationQueryStreamEvent event) {
        try {
            subscriber.onEvent(event);
        } catch (Exception ex) {
            entry.subscribers.remove(subscriber);
        }
    }

    private void completeSubscribers(RunEntry entry) {
        List<ConversationRunSubscriber> subscribers = new ArrayList<>(entry.subscribers);
        entry.subscribers.clear();
        for (ConversationRunSubscriber subscriber : subscribers) {
            try {
                subscriber.onComplete();
            } catch (RuntimeException ignored) {
                // A disconnected transport must not prevent other subscribers from completing.
            }
        }
    }

    private Optional<RunEntry> findLocalEntry(String runId,
                                              String sessionCode,
                                              String roundCode,
                                              Long userId) {
        if (StringUtils.hasText(runId)) {
            return ownedRun(runId, userId);
        }
        return runs.values().stream()
                .filter(entry -> userId != null && userId.equals(entry.userId))
                .filter(entry -> !StringUtils.hasText(sessionCode) || sessionCode.equals(entry.sessionCode))
                .filter(entry -> !StringUtils.hasText(roundCode) || roundCode.equals(entry.roundCode))
                .max(Comparator.comparing(entry -> entry.createdAt));
    }

    private Optional<RunEntry> ownedRun(String runId, Long userId) {
        RunEntry entry = runs.get(runId);
        if (entry == null || userId == null || !userId.equals(entry.userId)) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    private void cleanupExpiredRuns() {
        Instant threshold = Instant.now().minus(properties.getTerminalTtl());
        runs.entrySet().removeIf(item -> {
            RunEntry entry = item.getValue();
            return entry.state.isTerminal() && entry.finishedAt != null && entry.finishedAt.isBefore(threshold);
        });
    }

    private long parseSequence(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            return 0L;
        }
        try {
            int separator = eventId.indexOf('.');
            return Long.parseLong(separator < 0 ? eventId : eventId.substring(0, separator));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private ConversationQueryStreamEvent runtimeEvent(String type, String status, String message) {
        ConversationQueryStreamEvent event = new ConversationQueryStreamEvent();
        event.setEventType(type);
        event.setSource("RUNTIME");
        event.setPhase(status);
        event.setStatus(status);
        event.setMessage(message);
        return event;
    }

    private static final class RunEntry {

        private final String runId;
        private final String ownerNodeId;
        private final String requestId;
        private final Long userId;
        private final Instant createdAt = Instant.now();
        private final ConversationCancellationToken cancellation;
        private final AtomicLong eventSequence = new AtomicLong();
        private final Deque<ConversationQueryStreamEvent> events = new ArrayDeque<>();
        private final List<ConversationRunSubscriber> subscribers = new CopyOnWriteArrayList<>();

        private volatile String sessionCode;
        private volatile String roundCode;
        private volatile ConversationRunState state = ConversationRunState.ACCEPTED;
        private volatile Instant startedAt;
        private volatile Instant finishedAt;
        private volatile String error;
        private volatile String lastEventType;
        private volatile Future<?> future;

        private RunEntry(String runId,
                         ConversationQueryCommand command,
                         String ownerNodeId,
                         java.util.function.BooleanSupplier externalCancellation) {
            this.runId = runId;
            this.ownerNodeId = ownerNodeId;
            this.requestId = command.getTraceId();
            this.userId = command.getUserId();
            this.sessionCode = command.getSessionCode();
            this.roundCode = command.getRoundCode();
            this.cancellation = new ConversationCancellationToken(externalCancellation);
        }

        private ConversationRunSnapshot snapshot() {
            return new ConversationRunSnapshot(
                    runId,
                    ownerNodeId,
                    requestId,
                    userId,
                    sessionCode,
                    roundCode,
                    state,
                    createdAt,
                    startedAt,
                    finishedAt,
                    error
            );
        }
    }
}
