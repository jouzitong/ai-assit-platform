package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryScope;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts Provider locators into an authenticated, opaque UI reference.
 *
 * <p>Provider IDs never become user-composable route parameters. The decoded owner is still
 * compared with the current authenticated identity before every operation.</p>
 */
@Component
public class MemoryReferenceCodec {

    private static final String PREFIX = "m1.";
    private static final int FORMAT_VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final ConversationMemoryProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public MemoryReferenceCodec(ConversationMemoryProperties properties) {
        this.properties = properties;
    }

    public String encode(MemoryReference reference) {
        validate(reference);
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(PREFIX.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(serialize(reference));
            byte[] token = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, token, 0, iv.length);
            System.arraycopy(encrypted, 0, token, iv.length, encrypted.length);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create opaque Memory reference", ex);
        }
    }

    public MemoryReference decode(String value) {
        if (!StringUtils.hasText(value) || !value.startsWith(PREFIX)) {
            throw invalidReference();
        }
        try {
            byte[] token = Base64.getUrlDecoder().decode(value.substring(PREFIX.length()));
            if (token.length <= IV_BYTES) {
                throw invalidReference();
            }
            byte[] iv = java.util.Arrays.copyOfRange(token, 0, IV_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(token, IV_BYTES, token.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(PREFIX.getBytes(StandardCharsets.UTF_8));
            return deserialize(cipher.doFinal(encrypted));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidReference();
        }
    }

    private byte[] serialize(MemoryReference reference) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(FORMAT_VERSION);
            output.writeUTF(reference.tenantId());
            output.writeLong(reference.userId());
            output.writeUTF(reference.scope().name());
            output.writeUTF(reference.memoryId());
            output.writeUTF(reference.messageId());
            output.writeUTF(nullToEmpty(reference.sessionId()));
            output.writeUTF(reference.memoryType() == null ? "" : reference.memoryType().name());
        }
        return bytes.toByteArray();
    }

    private MemoryReference deserialize(byte[] value) throws Exception {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(value))) {
            if (input.readInt() != FORMAT_VERSION) {
                throw invalidReference();
            }
            String tenantId = input.readUTF();
            long userId = input.readLong();
            MemoryScope scope = MemoryScope.valueOf(input.readUTF());
            String memoryId = input.readUTF();
            String messageId = input.readUTF();
            String sessionId = emptyToNull(input.readUTF());
            String type = input.readUTF();
            MemoryType memoryType = StringUtils.hasText(type) ? MemoryType.valueOf(type) : null;
            if (input.available() != 0) {
                throw invalidReference();
            }
            MemoryReference reference = new MemoryReference(
                    tenantId, userId, scope, memoryId, messageId, sessionId, memoryType);
            validate(reference);
            return reference;
        }
    }

    private SecretKeySpec key() throws Exception {
        if (!StringUtils.hasText(properties.getIdentitySalt())) {
            throw new IllegalStateException("ai.chat.memory.identity-salt is required for Memory references");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update("conversation-memory-ref\u001f".getBytes(StandardCharsets.UTF_8));
        byte[] key = digest.digest(properties.getIdentitySalt().trim().getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }

    private void validate(MemoryReference reference) {
        if (reference == null || !StringUtils.hasText(reference.tenantId()) || reference.userId() == null
                || reference.scope() == null || !StringUtils.hasText(reference.memoryId())
                || !StringUtils.hasText(reference.messageId())) {
            throw invalidReference();
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private IllegalArgumentException invalidReference() {
        return new IllegalArgumentException("Invalid Memory reference");
    }

    public record MemoryReference(String tenantId,
                                  Long userId,
                                  MemoryScope scope,
                                  String memoryId,
                                  String messageId,
                                  String sessionId,
                                  MemoryType memoryType) {
    }
}
