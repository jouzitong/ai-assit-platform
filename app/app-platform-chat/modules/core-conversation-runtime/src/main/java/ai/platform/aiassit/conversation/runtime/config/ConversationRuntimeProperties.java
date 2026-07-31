package ai.platform.aiassit.conversation.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@ConfigurationProperties(prefix = "ai.chat.runtime")
public class ConversationRuntimeProperties {

    public enum Mode {
        LOCAL,
        REDIS
    }

    private final String generatedNodeId = "chat-" + UUID.randomUUID().toString().replace("-", "");

    private Mode mode = Mode.LOCAL;

    private String nodeId;

    private int maxReplayEvents = 512;

    private Duration activeTtl = Duration.ofHours(2);

    private Duration terminalTtl = Duration.ofMinutes(30);

    private Redis redis = new Redis();

    public String resolvedNodeId() {
        return StringUtils.hasText(nodeId) ? nodeId.trim() : generatedNodeId;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public int getMaxReplayEvents() {
        return maxReplayEvents;
    }

    public void setMaxReplayEvents(int maxReplayEvents) {
        this.maxReplayEvents = maxReplayEvents;
    }

    public Duration getActiveTtl() {
        return activeTtl;
    }

    public void setActiveTtl(Duration activeTtl) {
        this.activeTtl = activeTtl;
    }

    public Duration getTerminalTtl() {
        return terminalTtl;
    }

    public void setTerminalTtl(Duration terminalTtl) {
        this.terminalTtl = terminalTtl;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public static class Redis {

        private String keyPrefix = "ai:chat:runtime";

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }
}
