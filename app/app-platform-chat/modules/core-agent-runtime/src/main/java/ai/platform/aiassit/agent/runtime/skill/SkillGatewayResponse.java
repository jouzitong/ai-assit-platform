package ai.platform.aiassit.agent.runtime.skill;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SkillGatewayResponse {
    String skillCode;
    Integer skillVersion;
    String path;
    String mediaType;
    String checksum;
    String encoding;
    String content;
}
