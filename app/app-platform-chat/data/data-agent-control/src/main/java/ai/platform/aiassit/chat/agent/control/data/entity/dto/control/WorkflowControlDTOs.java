package ai.platform.aiassit.chat.agent.control.data.entity.dto.control;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Contracts for a versioned, artifact-acceptance-only Workflow. */
public final class WorkflowControlDTOs {

    private WorkflowControlDTOs() {
    }

    @Data
    public static class Catalog {
        private Long id;
        private String code;
        private String name;
        private String description;
        private String status;
        private Boolean enabled;
        private Integer currentPublishedVersion;
        private Integer draftVersion;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /**
     * The management UI uses the flattened fields while {@code manifest/spec} is the canonical persisted form.
     * Accepting both makes the HTTP contract convenient without creating a second runtime definition format.
     */
    @Data
    public static class DraftRequest {
        private String apiVersion = "ai.platform/v1alpha1";
        private String kind = "ArtifactWorkflow";
        @Valid
        private Metadata metadata;
        @Valid
        private Spec spec;
        private String code;
        private String name;
        private String description;
        private Boolean enabled = Boolean.TRUE;
        @Valid
        private List<Artifact> artifacts = new ArrayList<>();
        @Valid
        private List<Check> checks = new ArrayList<>();
        @Valid
        private CompletionPolicy completionPolicy = new CompletionPolicy();
        @Valid
        private RepairPolicy repairPolicy = new RepairPolicy();
    }

    @Data
    public static class Manifest {
        private String apiVersion = "ai.platform/v1alpha1";
        private String kind = "ArtifactWorkflow";
        @Valid
        private Metadata metadata = new Metadata();
        @Valid
        private Spec spec = new Spec();
    }

    @Data
    public static class Metadata {
        private String code;
        private Integer version;
        private String name;
        private String description;
        private Map<String, String> labels = new LinkedHashMap<>();
    }

    @Data
    public static class Spec {
        @Valid
        private List<Artifact> artifacts = new ArrayList<>();
        @Valid
        private List<Check> checks = new ArrayList<>();
        @Valid
        private CompletionPolicy completionPolicy = new CompletionPolicy();
        @Valid
        private RepairPolicy repairPolicy = new RepairPolicy();
    }

    @Data
    public static class Version {
        private Long id;
        private String workflowCode;
        private String code;
        private String name;
        private String description;
        private Boolean enabled;
        private Integer version;
        private Integer currentPublishedVersion;
        private Integer draftVersion;
        private String status;
        private String apiVersion;
        private String kind;
        private Metadata metadata;
        private Spec spec;
        private Manifest manifest;
        private List<Artifact> artifacts = new ArrayList<>();
        private List<Check> checks = new ArrayList<>();
        private CompletionPolicy completionPolicy;
        private RepairPolicy repairPolicy;
        private ValidationReportDTO validation;
        private String checksum;
        private LocalDateTime publishedAt;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class Artifact {
        @NotBlank
        private String code;
        private String name;
        @NotBlank
        private String artifactType;
        private String contentFormat;
        private Boolean required = Boolean.TRUE;
        private Boolean visible = Boolean.TRUE;
        private String schemaRef;
        private String templateRef;
        private Map<String, Object> inlineSchema = new LinkedHashMap<>();
        private String inlineTemplate;
    }

    @Data
    public static class Check {
        @NotBlank
        private String code;
        private String name;
        @NotBlank
        private String targetArtifact;
        @NotBlank
        private String checkerType;
        private String checkerRef;
        private String severity = "ERROR";
        private Boolean blocking = Boolean.TRUE;
        private Boolean retryable = Boolean.FALSE;
        private Map<String, Object> config = new LinkedHashMap<>();
    }

    @Data
    public static class CompletionPolicy {
        private Boolean requireAllRequiredArtifacts = Boolean.TRUE;
        private Boolean requireAllBlockingChecksPassed = Boolean.TRUE;
    }

    @Data
    public static class RepairPolicy {
        private Integer maxRepairAttempts = 0;
        private String onExhausted = "FAILED";
    }
}
