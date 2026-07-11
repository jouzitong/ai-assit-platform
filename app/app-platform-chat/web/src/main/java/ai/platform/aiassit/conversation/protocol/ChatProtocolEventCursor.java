package ai.platform.aiassit.conversation.protocol;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChatProtocolEventCursor {

    public String runtimeReplayCursor(String protocolEventId) {
        Cursor cursor = parse(protocolEventId);
        if (cursor.subSequence() <= 0L) {
            return protocolEventId;
        }
        return String.valueOf(Math.max(0L, cursor.sequence() - 1L));
    }

    public boolean isAfter(String candidateEventId, String lastProtocolEventId) {
        if (!StringUtils.hasText(lastProtocolEventId)) {
            return true;
        }
        Cursor candidate = parse(candidateEventId);
        Cursor last = parse(lastProtocolEventId);
        if (candidate.sequence() != last.sequence()) {
            return candidate.sequence() > last.sequence();
        }
        return candidate.subSequence() > last.subSequence();
    }

    private Cursor parse(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            return new Cursor(0L, 0L);
        }
        String[] parts = eventId.trim().split("\\.", 2);
        return new Cursor(parseLong(parts[0]), parts.length > 1 ? parseLong(parts[1]) : 0L);
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private record Cursor(long sequence, long subSequence) {
    }
}
