package ai.platform.aiassit.conversation.memory.config;

import ai.platform.aiassit.service.ai.api.memory.enums.MemoryProviderType;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Chat Memory control-plane settings. Provider URL and credentials are intentionally absent. */
@Data
@ConfigurationProperties(prefix = "ai.chat.memory")
public class ConversationMemoryProperties {

    private boolean enabled;
    private MemoryProviderType providerType = MemoryProviderType.RAGFLOW;
    private String clientKey = "ragflow";
    private String identitySalt;
    private int schemaVersion = 1;
    private boolean sessionEnabled = true;
    private boolean longTermEnabled = true;
    private List<MemoryType> sessionMemoryTypes = new ArrayList<>(
            List.of(MemoryType.RAW, MemoryType.SEMANTIC, MemoryType.EPISODIC));
    private List<MemoryType> longTermMemoryTypes = new ArrayList<>(
            List.of(MemoryType.RAW, MemoryType.SEMANTIC, MemoryType.PROCEDURAL));
    private String embeddingModel;
    private String extractionModel;
    private int memorySize = 5 * 1024 * 1024;
    private String forgettingPolicy = "FIFO";
    private Recall recall = new Recall();
    private Sync sync = new Sync();
    private Provision provision = new Provision();

    @Data
    public static class Recall {
        private boolean enabled = true;
        /** Off by default until the deployed RAGFlow idempotency contract is validated. */
        private boolean injectionEnabled;
        private int sessionTopN = 12;
        private int longTermTopN = 6;
        private double similarityThreshold = 0.2D;
        private double keywordWeight = 0.7D;
        private int timeoutMs = 800;
        private int tokenBudget = 3_000;
        private int perItemTokenLimit = 600;
    }

    @Data
    public static class Sync {
        private boolean shadowWriteEnabled = true;
        /**
         * Enables reconciliation-driven retry for uncertain writes. This must remain disabled
         * until the deployed Provider proves that externalId is queryable and idempotent.
         */
        private boolean providerIdempotencyEnabled;
        private int batchSize = 10;
        private int maxRetries = 5;
        private Duration leaseTimeout = Duration.ofMinutes(2);
        private Duration retryBaseDelay = Duration.ofSeconds(10);
        private long fixedDelayMs = 5_000L;
    }

    @Data
    public static class Provision {
        private Duration leaseTimeout = Duration.ofMinutes(3);
        private Duration verifyTtl = Duration.ofHours(6);
    }
}
