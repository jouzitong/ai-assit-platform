package ai.platform.aiassit.agent.runtime.skill;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class SkillGatewayRequest {
    private String path = "SKILL.md";
    private Map<String, Object> run = new LinkedHashMap<>();
}
