package ai.platform.aiassit.service.ai.spi.skill;

import java.util.Optional;

/** Published-only Skill file lookup used by the read-only Skill Gateway. */
public interface PublishedSkillResourceStore {
    Optional<PublishedSkillResource> findPublished(String skillCode, Integer skillVersion, String path);
}
