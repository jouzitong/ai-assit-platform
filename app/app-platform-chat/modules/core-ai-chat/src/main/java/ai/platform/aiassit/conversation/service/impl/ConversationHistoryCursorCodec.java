package ai.platform.aiassit.conversation.service.impl;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes the storage position as an opaque URL-safe cursor. It is not an authorization boundary:
 * every decoded position is still queried with the authenticated session and user predicates.
 */
@Component
public class ConversationHistoryCursorCodec {

    private static final String VERSION = "v1";

    public String encode(String sessionCode, Long userId, Long roundId) {
        if (!StringUtils.hasText(sessionCode) || userId == null || roundId == null || roundId <= 0) {
            return null;
        }
        String session = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sessionCode.trim().getBytes(StandardCharsets.UTF_8));
        String payload = VERSION + ":" + session + ":" + userId + ":" + roundId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public Long decode(String cursor, String expectedSessionCode, Long expectedUserId) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        if (cursor.length() > 256 || !StringUtils.hasText(expectedSessionCode) || expectedUserId == null) {
            throw new IllegalArgumentException("invalid history cursor");
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            String[] parts = payload.split(":", -1);
            if (parts.length != 4 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("invalid history cursor");
            }
            String sessionCode = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            long userId = Long.parseLong(parts[2]);
            long roundId = Long.parseLong(parts[3]);
            if (!expectedSessionCode.trim().equals(sessionCode) || expectedUserId.longValue() != userId
                    || roundId <= 0) {
                throw new IllegalArgumentException("invalid history cursor");
            }
            return roundId;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("invalid history cursor", ex);
        }
    }
}
