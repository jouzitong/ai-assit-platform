package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryScope;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Creates non-reversible stable Provider identities from trusted platform identity. */
@Component
public class ConversationMemoryIdentity {

    private final ConversationMemoryProperties properties;

    public ConversationMemoryIdentity(ConversationMemoryProperties properties) {
        this.properties = properties;
    }

    public String providerUserId(String tenantId, Long userId) {
        return "platform-user-" + digest(identitySource(tenantId, userId)).substring(0, 32);
    }

    public String memoryName(MemoryScope scope, String tenantId, Long userId) {
        String tenantHash = digest("tenant\u001f" + requireText(tenantId, "tenantId")).substring(0, 12);
        String userHash = digest(identitySource(tenantId, userId)).substring(0, 12);
        String prefix = scope == MemoryScope.LONG_TERM ? "chat-longterm" : "chat-session";
        return prefix + "-" + tenantHash + "-" + userHash + "-" + properties.getSchemaVersion();
    }

    private String identitySource(String tenantId, Long userId) {
        if (userId == null) {
            throw new IllegalStateException("Trusted Memory userId is required");
        }
        String salt = requireText(properties.getIdentitySalt(), "ai.chat.memory.identity-salt");
        return salt + "\u001f" + requireText(tenantId, "tenantId") + "\u001f" + userId;
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(field + " is required when Chat Memory is enabled");
        }
        return value.trim();
    }
}
