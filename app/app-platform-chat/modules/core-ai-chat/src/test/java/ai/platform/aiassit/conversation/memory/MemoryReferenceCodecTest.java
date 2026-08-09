package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.memory.MemoryReferenceCodec.MemoryReference;
import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryScope;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryReferenceCodecTest {

    @Test
    void roundTripsOpaqueReferenceWithoutPuttingProviderIdsInPlainText() {
        ConversationMemoryProperties properties = new ConversationMemoryProperties();
        properties.setIdentitySalt("test-only-memory-salt");
        MemoryReferenceCodec codec = new MemoryReferenceCodec(properties);
        MemoryReference source = new MemoryReference(
                "tenant-a", 42L, MemoryScope.SESSION, "rf-memory-1", "rf-message-9",
                "session-1", MemoryType.SEMANTIC);

        String encoded = codec.encode(source);

        assertThat(encoded).startsWith("m1.")
                .doesNotContain("rf-memory-1", "rf-message-9", "tenant-a");
        assertThat(codec.decode(encoded)).isEqualTo(source);
    }

    @Test
    void rejectsTamperedOrWronglyAuthenticatedReference() {
        ConversationMemoryProperties properties = new ConversationMemoryProperties();
        properties.setIdentitySalt("test-only-memory-salt");
        MemoryReferenceCodec codec = new MemoryReferenceCodec(properties);
        String encoded = codec.encode(new MemoryReference(
                "tenant-a", 42L, MemoryScope.LONG_TERM, "rf-memory-1", "rf-message-9",
                null, MemoryType.PROCEDURAL));

        String tampered = encoded.substring(0, encoded.length() - 1)
                + (encoded.endsWith("A") ? "B" : "A");
        assertThatThrownBy(() -> codec.decode(tampered))
                .isInstanceOf(IllegalArgumentException.class);

        ConversationMemoryProperties otherProperties = new ConversationMemoryProperties();
        otherProperties.setIdentitySalt("different-salt");
        assertThatThrownBy(() -> new MemoryReferenceCodec(otherProperties).decode(encoded))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
