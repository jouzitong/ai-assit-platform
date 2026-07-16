package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, run-scoped capability grants consumed by localhost Tool/Skill gateways.
 * Workers cannot use a user's bearer token to access capabilities outside the frozen snapshot.
 */
@Service
public class AgentCapabilityGrantService {

    private final Map<String, Grant> grants = new ConcurrentHashMap<>();

    public void register(String runId,
                         Long userId,
                         AgentDefinitionSnapshot snapshot,
                         Duration ttl) {
        if (!StringUtils.hasText(runId) || userId == null || snapshot == null) return;
        Duration bounded = ttl == null ? Duration.ofMinutes(5) : ttl;
        if (bounded.isNegative() || bounded.isZero()) bounded = Duration.ofMinutes(5);
        if (bounded.compareTo(Duration.ofMinutes(30)) > 0) bounded = Duration.ofMinutes(30);
        grants.put(runId, new Grant(
                userId,
                snapshot.getSnapshotHash(),
                capabilityKeys(snapshot.getResolvedCapabilities(), "skills"),
                capabilityKeys(snapshot.getResolvedCapabilities(), "tools"),
                Instant.now().plus(bounded)));
        purgeExpired();
    }

    public void revoke(String runId) {
        if (StringUtils.hasText(runId)) grants.remove(runId);
    }

    public boolean allows(String runId,
                          Long userId,
                          String snapshotHash,
                          String capabilityType,
                          String code,
                          Integer version) {
        if (!StringUtils.hasText(runId) || userId == null || !StringUtils.hasText(code) || version == null) {
            return false;
        }
        Grant grant = grants.get(runId);
        if (grant == null || grant.expiresAt().isBefore(Instant.now())) {
            grants.remove(runId);
            return false;
        }
        if (!userId.equals(grant.userId()) || !StringUtils.hasText(snapshotHash)
                || !snapshotHash.equals(grant.snapshotHash())) {
            return false;
        }
        String key = key(code, version);
        return "skill".equalsIgnoreCase(capabilityType)
                ? grant.skills().contains(key)
                : "tool".equalsIgnoreCase(capabilityType) && grant.tools().contains(key);
    }

    private Set<String> capabilityKeys(Map<String, Object> capabilities, String type) {
        Object source = capabilities == null ? null : capabilities.get(type);
        if (!(source instanceof Collection<?> values)) return Set.of();
        Set<String> keys = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> item)) continue;
            String code = text(item.get("code"));
            Integer version = integer(item.get("version"));
            if (StringUtils.hasText(code) && version != null) keys.add(key(code, version));
        }
        return Set.copyOf(keys);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        grants.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String key(String code, Integer version) {
        return code.trim() + "@" + version;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record Grant(Long userId,
                         String snapshotHash,
                         Set<String> skills,
                         Set<String> tools,
                         Instant expiresAt) {
    }
}
