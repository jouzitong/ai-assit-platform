package ai.platform.aiassit.service.ai.spi.skill;

import lombok.Builder;
import lombok.Value;

/** One immutable file from a published Skill package. */
@Value
@Builder
public class PublishedSkillResource {
    String skillCode;
    Integer skillVersion;
    String path;
    String mediaType;
    String checksum;
    byte[] content;
}
