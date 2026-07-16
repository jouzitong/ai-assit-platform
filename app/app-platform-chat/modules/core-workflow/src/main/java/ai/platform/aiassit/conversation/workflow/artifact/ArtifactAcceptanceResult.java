package ai.platform.aiassit.conversation.workflow.artifact;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Result of checking an Agent-produced Artifact set against a Workflow contract. */
@Data
public class ArtifactAcceptanceResult {
    private boolean accepted;
    private boolean repairable;
    private boolean inputRequired;
    private int maxRepairAttempts;
    private String onExhausted;
    private String repairMessage;
    private List<Map<String, Object>> artifacts = new ArrayList<>();
    private List<ArtifactCheckResult> checks = new ArrayList<>();
}
