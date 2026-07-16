package ai.platform.aiassit.conversation.workflow.artifact;

import java.util.List;
import java.util.Map;

/** Workflow is an Artifact contract and never an execution graph. */
public interface ArtifactAcceptanceService {
    ArtifactAcceptanceResult accept(Map<String, Object> workflowSnapshot,
                                    List<Map<String, Object>> artifacts,
                                    String finalAnswer);
}
