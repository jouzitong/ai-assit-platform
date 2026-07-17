package ai.platform.aiassit.chat.agent.control.data.importer;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived quarantine for successfully inspected packages.
 * A random, single-use draft id prevents import from accepting a different ZIP than the one inspected.
 */
@Component
public class SkillPackageDraftStore {

    private static final int MAX_DRAFTS = 32;
    private static final Duration TTL = Duration.ofMinutes(15);
    private final Map<String, Draft> drafts = new ConcurrentHashMap<>();

    public String quarantine(InspectedSkillPackage inspected) {
        if (inspected == null || !inspected.isValid()) {
            throw new IllegalArgumentException("Only a valid inspected Skill package can be quarantined");
        }
        purgeExpired();
        if (drafts.size() >= MAX_DRAFTS) {
            Draft oldest = drafts.values().stream().min(Comparator.comparing(Draft::createdAt)).orElse(null);
            if (oldest != null) drafts.remove(oldest.id(), oldest);
        }
        String id = UUID.randomUUID().toString();
        drafts.put(id, new Draft(id, Instant.now(), inspected));
        return id;
    }

    /** Claims and removes a package so the same inspection cannot create multiple versions. */
    public Optional<InspectedSkillPackage> claim(String draftId) {
        purgeExpired();
        if (draftId == null || draftId.isBlank()) return Optional.empty();
        Draft draft = drafts.remove(draftId.trim());
        return draft == null ? Optional.empty() : Optional.of(draft.packageSnapshot());
    }

    private void purgeExpired() {
        Instant threshold = Instant.now().minus(TTL);
        drafts.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(threshold));
    }

    private record Draft(String id, Instant createdAt, InspectedSkillPackage packageSnapshot) {
    }
}
