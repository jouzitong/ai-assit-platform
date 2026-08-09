package ai.platform.aiassit.conversation.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationHistoryCursorCodecTest {

    private final ConversationHistoryCursorCodec codec = new ConversationHistoryCursorCodec();

    @Test
    void roundTripsAnOpaqueUrlSafeCursor() {
        String cursor = codec.encode("session:中文/1", 42L, 99L);

        assertThat(cursor).doesNotContain("=", "/", "+");
        assertThat(codec.decode(cursor, "session:中文/1", 42L)).isEqualTo(99L);
    }

    @Test
    void blankCursorMeansTheLatestPage() {
        assertThat(codec.decode(null, "session-1", 42L)).isNull();
        assertThat(codec.decode("  ", "session-1", 42L)).isNull();
    }

    @Test
    void rejectsMalformedOrCrossOwnerCursors() {
        String cursor = codec.encode("session-1", 42L, 99L);

        assertThatThrownBy(() -> codec.decode("not-base64!", "session-1", 42L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(cursor, "session-2", 42L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(cursor, "session-1", 43L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("a".repeat(257), "session-1", 42L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToEncodeIncompleteStoragePositions() {
        assertThat(codec.encode(null, 42L, 1L)).isNull();
        assertThat(codec.encode("session-1", null, 1L)).isNull();
        assertThat(codec.encode("session-1", 42L, 0L)).isNull();
    }
}
