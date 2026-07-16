package ai.platform.aiassit.conversation.workflow.artifact;

import lombok.Builder;
import lombok.Value;

/** One deterministic or delegated Artifact check result. */
@Value
@Builder
public class ArtifactCheckResult {
    String checkCode;
    String targetArtifact;
    String checkerType;
    String severity;
    boolean blocking;
    boolean retryable;
    boolean passed;
    String status;
    String message;
}
