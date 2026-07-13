package ai.platform.aiassit.conversation.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatProtocolEventCursorTest {

    private final ChatProtocolEventCursor cursor = new ChatProtocolEventCursor();

    @Test
    void replaysSourceEventWhenOnlySomeProjectedEventsWereReceived() {
        assertThat(cursor.runtimeReplayCursor("3.1")).isEqualTo("2");
        assertThat(cursor.isAfter("3.1", "3.1")).isFalse();
        assertThat(cursor.isAfter("3.2", "3.1")).isTrue();
        assertThat(cursor.isAfter("4", "3.1")).isTrue();
    }

    @Test
    void keepsNormalRuntimeCursorForSingleProjectionEvent() {
        assertThat(cursor.runtimeReplayCursor("8")).isEqualTo("8");
        assertThat(cursor.isAfter("8", "8")).isFalse();
        assertThat(cursor.isAfter("9", "8")).isTrue();
    }

    @Test
    void rebasesPersistedReplayAfterClientCursor() {
        assertThat(cursor.persistedReplayEventId("1", "8.3", 1)).isEqualTo("9");
        assertThat(cursor.persistedReplayEventId("2", "8.3", 2)).isEqualTo("10");
        assertThat(cursor.persistedReplayEventId("5", null, 1)).isEqualTo("5");
    }
}
