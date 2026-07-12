package ai.platform.aiassit.conversation.dto.protocol;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class RoundThinkingResponse {

    private String schemaVersion = "round-thinking.v1";

    private String sessionCode;

    private String roundCode;

    private String status;

    private String summary = "思考过程";

    private List<Map<String, Object>> nodes = new ArrayList<>();

    private List<Map<String, Object>> activities = new ArrayList<>();

    private Map<String, Object> ext = new LinkedHashMap<>();
}
