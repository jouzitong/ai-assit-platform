package ai.platform.aiassit.conversation.dto.protocol;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class RoundThinkingResponse {

    private String schemaVersion = "round-thinking.v2";

    private String sessionCode;

    private String roundCode;

    private String status;

    private String summary = "Agent 执行过程";

    private List<Map<String, Object>> agents = new ArrayList<>();

    private List<Map<String, Object>> activities = new ArrayList<>();

    private List<Map<String, Object>> artifacts = new ArrayList<>();

    private Map<String, Object> ext = new LinkedHashMap<>();
}
