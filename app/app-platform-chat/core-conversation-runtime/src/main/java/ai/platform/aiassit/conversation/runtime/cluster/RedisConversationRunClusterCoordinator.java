package ai.platform.aiassit.conversation.runtime.cluster;

import ai.platform.aiassit.conversation.runtime.config.ConversationRuntimeProperties;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscriber;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscription;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunState;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class RedisConversationRunClusterCoordinator implements ConversationRunClusterCoordinator {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ConversationRuntimeProperties properties;
    private final String nodeId;
    private final String keyPrefix;
    private final ConcurrentMap<String, CopyOnWriteArrayList<RemoteSubscription>> subscriptions =
            new ConcurrentHashMap<>();

    private volatile Consumer<String> localCancelHandler = runId -> { };

    public RedisConversationRunClusterCoordinator(StringRedisTemplate redis,
                                                  ObjectMapper objectMapper,
                                                  RedisMessageListenerContainer listenerContainer,
                                                  ConversationRuntimeProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.nodeId = properties.resolvedNodeId();
        this.keyPrefix = normalizePrefix(properties.getRedis().getKeyPrefix());
        listenerContainer.addMessageListener(this::onEventMessage, new ChannelTopic(eventChannel()));
        listenerContainer.addMessageListener(this::onCancelMessage, new ChannelTopic(cancelChannel()));
    }

    @Override
    public String nodeId() {
        return nodeId;
    }

    @Override
    public boolean distributed() {
        return true;
    }

    @Override
    public void registerLocalCancelHandler(Consumer<String> cancelHandler) {
        this.localCancelHandler = cancelHandler == null ? runId -> { } : cancelHandler;
    }

    @Override
    public void save(ConversationRunSnapshot snapshot) {
        if (snapshot == null || !StringUtils.hasText(snapshot.runId())) {
            return;
        }
        try {
            saveInternal(snapshot);
        } catch (RuntimeException ex) {
            log.warn("failed to save distributed conversation run, runId={}", snapshot.runId(), ex);
        }
    }

    @Override
    public void publish(ConversationRunSnapshot snapshot, ConversationQueryStreamEvent event) {
        if (snapshot == null || event == null || !StringUtils.hasText(snapshot.runId())) {
            return;
        }
        try {
            saveInternal(snapshot);
            String eventJson = write(event);
            String eventKey = eventKey(snapshot.runId());
            redis.opsForList().rightPush(eventKey, eventJson);
            int maxEvents = Math.max(1, properties.getMaxReplayEvents());
            redis.opsForList().trim(eventKey, -maxEvents, -1);
            redis.expire(eventKey, ttl(snapshot));
            redis.convertAndSend(eventChannel(), eventJson);
        } catch (RuntimeException ex) {
            log.warn("failed to publish distributed conversation event, runId={}, eventType={}",
                    snapshot.runId(), event.getEventType(), ex);
        }
    }

    @Override
    public Optional<ConversationRunSnapshot> find(String runId,
                                                  String sessionCode,
                                                  String roundCode,
                                                  Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            if (StringUtils.hasText(runId)) {
                return readSnapshot(runId).filter(snapshot -> userId.equals(snapshot.userId()));
            }
            String indexKey = indexKey(userId, sessionCode, roundCode);
            Set<String> candidates = redis.opsForZSet().reverseRange(indexKey, 0, 19);
            if (candidates == null) {
                return Optional.empty();
            }
            for (String candidate : candidates) {
                Optional<ConversationRunSnapshot> snapshot = readSnapshot(candidate)
                        .filter(value -> userId.equals(value.userId()))
                        .filter(value -> !StringUtils.hasText(sessionCode)
                                || sessionCode.equals(value.sessionCode()))
                        .filter(value -> !StringUtils.hasText(roundCode)
                                || roundCode.equals(value.roundCode()));
                if (snapshot.isPresent()) {
                    return snapshot;
                }
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("failed to find distributed conversation run, runId={}", runId, ex);
            throw new IllegalStateException("distributed conversation runtime is unavailable", ex);
        }
    }

    @Override
    public ConversationRunSubscription subscribe(String runId,
                                                 Long userId,
                                                 String lastEventId,
                                                 ConversationRunSubscriber subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("run subscriber is required");
        }
        ConversationRunSnapshot snapshot = find(runId, null, null, userId)
                .orElseThrow(() -> new IllegalArgumentException("conversation run not found"));
        RemoteSubscription subscription = new RemoteSubscription(
                runId,
                subscriber,
                parseSequence(lastEventId)
        );
        subscriptions.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(subscription);
        try {
            List<String> storedEvents = redis.opsForList().range(eventKey(runId), 0, -1);
            List<ConversationQueryStreamEvent> replay = new ArrayList<>();
            if (storedEvents != null) {
                for (String storedEvent : storedEvents) {
                    replay.add(read(storedEvent, ConversationQueryStreamEvent.class));
                }
            }
            subscription.finishReplay(replay, snapshot.state() != null && snapshot.state().isTerminal());
            return subscription;
        } catch (RuntimeException ex) {
            subscription.close();
            throw new IllegalStateException("failed to subscribe distributed conversation run", ex);
        }
    }

    @Override
    public boolean requestCancel(ConversationRunSnapshot snapshot) {
        if (snapshot == null || snapshot.state() == null || snapshot.state().isTerminal()) {
            return false;
        }
        try {
            redis.opsForValue().set(cancelKey(snapshot.runId()), "1", properties.getActiveTtl());
            saveInternal(withState(snapshot, ConversationRunState.CANCELLING));
            redis.convertAndSend(cancelChannel(), snapshot.runId());
            return true;
        } catch (RuntimeException ex) {
            log.warn("failed to request distributed conversation cancellation, runId={}", snapshot.runId(), ex);
            return false;
        }
    }

    @Override
    public boolean isCancelRequested(String runId) {
        if (!StringUtils.hasText(runId)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(cancelKey(runId)));
        } catch (RuntimeException ex) {
            log.warn("failed to check distributed conversation cancellation, runId={}", runId, ex);
            return false;
        }
    }

    private void saveInternal(ConversationRunSnapshot snapshot) {
        Duration ttl = ttl(snapshot);
        redis.opsForValue().set(runKey(snapshot.runId()), write(snapshot), ttl);
        redis.expire(eventKey(snapshot.runId()), ttl);
        if (snapshot.state() != null && snapshot.state().isTerminal()) {
            redis.delete(cancelKey(snapshot.runId()));
        }
        if (snapshot.userId() == null) {
            return;
        }
        double score = snapshot.createdAt() == null
                ? System.currentTimeMillis()
                : snapshot.createdAt().toEpochMilli();
        addIndex(indexKey(snapshot.userId(), null, null), snapshot.runId(), score);
        if (StringUtils.hasText(snapshot.sessionCode())) {
            addIndex(indexKey(snapshot.userId(), snapshot.sessionCode(), null), snapshot.runId(), score);
        }
        if (StringUtils.hasText(snapshot.roundCode())) {
            addIndex(indexKey(snapshot.userId(), null, snapshot.roundCode()), snapshot.runId(), score);
        }
        if (StringUtils.hasText(snapshot.sessionCode()) && StringUtils.hasText(snapshot.roundCode())) {
            addIndex(indexKey(snapshot.userId(), snapshot.sessionCode(), snapshot.roundCode()), snapshot.runId(), score);
        }
    }

    private void addIndex(String key, String runId, double score) {
        redis.opsForZSet().add(key, runId, score);
        redis.expire(key, properties.getActiveTtl());
    }

    private Optional<ConversationRunSnapshot> readSnapshot(String runId) {
        String value = redis.opsForValue().get(runKey(runId));
        return StringUtils.hasText(value)
                ? Optional.of(read(value, ConversationRunSnapshot.class))
                : Optional.empty();
    }

    private void onEventMessage(Message message, byte[] pattern) {
        try {
            ConversationQueryStreamEvent event = read(body(message), ConversationQueryStreamEvent.class);
            if (!StringUtils.hasText(event.getRunId())) {
                return;
            }
            List<RemoteSubscription> runSubscriptions = subscriptions.get(event.getRunId());
            if (runSubscriptions != null) {
                for (RemoteSubscription subscription : runSubscriptions) {
                    subscription.acceptLive(event);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("failed to consume distributed conversation event", ex);
        }
    }

    private void onCancelMessage(Message message, byte[] pattern) {
        String runId = body(message);
        if (StringUtils.hasText(runId)) {
            try {
                localCancelHandler.accept(runId);
            } catch (RuntimeException ex) {
                log.warn("failed to consume distributed cancellation, runId={}", runId, ex);
            }
        }
    }

    private ConversationRunSnapshot withState(ConversationRunSnapshot snapshot, ConversationRunState state) {
        return new ConversationRunSnapshot(
                snapshot.runId(),
                snapshot.ownerNodeId(),
                snapshot.requestId(),
                snapshot.userId(),
                snapshot.sessionCode(),
                snapshot.roundCode(),
                state,
                snapshot.createdAt(),
                snapshot.startedAt(),
                snapshot.finishedAt(),
                snapshot.error()
        );
    }

    private Duration ttl(ConversationRunSnapshot snapshot) {
        return snapshot.state() != null && snapshot.state().isTerminal()
                ? properties.getTerminalTtl()
                : properties.getActiveTtl();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize conversation runtime value", ex);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to deserialize conversation runtime value", ex);
        }
    }

    private String body(Message message) {
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }

    private String runKey(String runId) {
        return keyPrefix + ":run:" + runId;
    }

    private String eventKey(String runId) {
        return keyPrefix + ":events:" + runId;
    }

    private String cancelKey(String runId) {
        return keyPrefix + ":cancel:" + runId;
    }

    private String indexKey(Long userId, String sessionCode, String roundCode) {
        StringBuilder key = new StringBuilder(keyPrefix).append(":index:user:").append(userId);
        if (StringUtils.hasText(sessionCode)) {
            key.append(":session:").append(sessionCode);
        }
        if (StringUtils.hasText(roundCode)) {
            key.append(":round:").append(roundCode);
        }
        key.append(":runs");
        return key.toString();
    }

    private String eventChannel() {
        return keyPrefix + ":channel:events";
    }

    private String cancelChannel() {
        return keyPrefix + ":channel:cancel";
    }

    private String normalizePrefix(String prefix) {
        String normalized = StringUtils.hasText(prefix) ? prefix.trim() : "ai:chat:runtime";
        while (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private long parseSequence(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            return 0L;
        }
        try {
            return Long.parseLong(eventId);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private boolean terminalEvent(ConversationQueryStreamEvent event) {
        return event != null && ("complete".equals(event.getEventType())
                || "error".equals(event.getEventType())
                || "run.cancelled".equals(event.getEventType()));
    }

    private final class RemoteSubscription implements ConversationRunSubscription {

        private final String runId;
        private final ConversationRunSubscriber subscriber;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final List<ConversationQueryStreamEvent> pending = new ArrayList<>();

        private boolean replaying = true;
        private long lastSequence;

        private RemoteSubscription(String runId,
                                   ConversationRunSubscriber subscriber,
                                   long lastSequence) {
            this.runId = runId;
            this.subscriber = subscriber;
            this.lastSequence = lastSequence;
        }

        private synchronized void acceptLive(ConversationQueryStreamEvent event) {
            if (closed.get()) {
                return;
            }
            if (replaying) {
                pending.add(event);
                return;
            }
            deliver(event);
        }

        private synchronized void finishReplay(List<ConversationQueryStreamEvent> replay,
                                               boolean terminalSnapshot) {
            if (closed.get()) {
                return;
            }
            List<ConversationQueryStreamEvent> merged = new ArrayList<>(replay);
            merged.addAll(pending);
            pending.clear();
            merged.sort(Comparator.comparingLong(event -> parseSequence(event.getEventId())));
            replaying = false;
            for (ConversationQueryStreamEvent event : merged) {
                deliver(event);
                if (closed.get()) {
                    return;
                }
            }
            if (terminalSnapshot) {
                complete();
            }
        }

        private void deliver(ConversationQueryStreamEvent event) {
            long sequence = parseSequence(event.getEventId());
            if (sequence <= lastSequence || closed.get()) {
                return;
            }
            lastSequence = sequence;
            try {
                subscriber.onEvent(event);
                if (terminalEvent(event)) {
                    complete();
                }
            } catch (Exception ex) {
                close();
            }
        }

        private void complete() {
            if (closed.compareAndSet(false, true)) {
                remove(this);
                try {
                    subscriber.onComplete();
                } catch (RuntimeException ex) {
                    log.debug("distributed conversation subscriber completion failed, runId={}", runId, ex);
                }
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                remove(this);
            }
        }
    }

    private void remove(RemoteSubscription subscription) {
        CopyOnWriteArrayList<RemoteSubscription> runSubscriptions = subscriptions.get(subscription.runId);
        if (runSubscriptions == null) {
            return;
        }
        runSubscriptions.remove(subscription);
        if (runSubscriptions.isEmpty()) {
            subscriptions.remove(subscription.runId, runSubscriptions);
        }
    }
}
