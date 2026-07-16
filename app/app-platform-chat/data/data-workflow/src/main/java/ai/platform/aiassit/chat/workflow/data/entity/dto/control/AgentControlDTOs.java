package ai.platform.aiassit.chat.workflow.data.entity.dto.control;

import ai.platform.aiassit.chat.workflow.data.enums.AgentRuntimeType;
import ai.platform.aiassit.chat.workflow.data.enums.DefinitionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API contracts for Agent catalog, immutable versions and product-entry bindings. */
public final class AgentControlDTOs {

    private AgentControlDTOs() {
    }

    @Data
    public static class Catalog {
        private Long id;
        private String code;
        private String name;
        private String description;
        private Integer currentVersion;
        private Integer currentPublishedVersion;
        private Integer draftVersion;
        private String status;
        private Boolean enabled;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class CreateRequest {
        @NotBlank
        private String code;
        @NotBlank
        private String name;
        private String description;
        @Valid
        private Manifest manifest;
        private String apiVersion;
        private String kind;
        @Valid
        private Metadata metadata;
        @Valid
        private Spec spec;
    }

    @Data
    public static class UpdateRequest {
        @NotBlank
        private String name;
        private String description;
        private Boolean enabled;
        @Valid
        private Manifest manifest;
        private String apiVersion;
        private String kind;
        @Valid
        private Metadata metadata;
        @Valid
        private Spec spec;
    }

    @Data
    public static class VersionCreateRequest {
        @Valid
        private Manifest manifest;
        private String apiVersion;
        private String kind;
        @Valid
        private Metadata metadata;
        @Valid
        private Spec spec;
    }

    @Data
    public static class Version {
        private Long id;
        private String agentCode;
        private String code;
        private String name;
        private String description;
        private Boolean enabled;
        private Integer version;
        private Integer draftVersion;
        private Integer currentPublishedVersion;
        private String status;
        private Manifest manifest;
        private String apiVersion;
        private String kind;
        private Metadata metadata;
        private Spec spec;
        private ValidationReportDTO validation;
        private String checksum;
        private LocalDateTime publishedAt;
    }

    @Data
    public static class EntryBindingRequest {
        @NotBlank
        private String agentCode;
        @NotNull
        private Integer agentVersion;
        @NotNull
        private AgentRuntimeType runtimeType;
        private String sdkVersion;
        private Integer priority = 100;
        private Boolean enabled = Boolean.TRUE;
        private Map<String, Object> config = new LinkedHashMap<>();
    }

    @Data
    public static class EntryBinding {
        private Long id;
        private String entryCode;
        private String agentCode;
        private Integer agentVersion;
        private AgentRuntimeType runtimeType;
        private String sdkVersion;
        private Integer priority;
        private Boolean enabled;
        private Map<String, Object> config = new LinkedHashMap<>();
    }

    /** UI-level entry selection; runtime and SDK remain server-owned binding details. */
    @Data
    public static class EntrySelectionRequest {
        private String entryCode;
        @NotBlank
        private String agentCode;
        private String versionStrategy = "LATEST_PUBLISHED";
        private Integer pinnedVersion;
        private Boolean enabled = Boolean.TRUE;
    }

    @Data
    public static class EntrySelection {
        private String entryCode;
        private String agentCode;
        private String versionStrategy;
        private Integer pinnedVersion;
        private Boolean enabled;
        private LocalDateTime updateTime;
    }

    /** Runtime-neutral declaration shared by Python and TypeScript adapters. */
    @Data
    public static class Manifest {
        private String apiVersion = "ai.platform/v1alpha1";
        private String kind = "Agent";
        @NotNull
        @Valid
        private Metadata metadata = new Metadata();
        @NotNull
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
        @NotNull
        @Valid
        private Instructions instructions = new Instructions();
        @NotNull
        @Valid
        private ModelRef model;
        private List<CapabilityRef> skillRefs = new ArrayList<>();
        private List<CapabilityRef> toolRefs = new ArrayList<>();
        private List<CapabilityRef> knowledgeRefs = new ArrayList<>();
        private List<CapabilityRef> mcpRefs = new ArrayList<>();
        private Collaboration collaboration = new Collaboration();
        private Guardrails guardrails = new Guardrails();
        private Output output = new Output();
        private RuntimeDefaults runtimeDefaults = new RuntimeDefaults();
        private Map<String, Object> extensions = new LinkedHashMap<>();
    }

    @Data
    public static class Instructions {
        private String type = "inline";
        private String text;
        private String ref;
    }

    @Data
    public static class ModelRef {
        @NotBlank
        private String ref;
        private Map<String, Object> settings = new LinkedHashMap<>();
    }

    @Data
    public static class CapabilityRef {
        @NotBlank
        private String ref;
        private Integer version;
        private Boolean required = Boolean.FALSE;
        private Boolean enabled = Boolean.TRUE;
        private String alias;
        private String contentHash;
    }

    @Data
    public static class CollaboratorRef {
        @NotBlank
        private String targetAgentRef;
        private String mode;
        private String toolName;
        private String description;
    }

    @Data
    public static class Collaboration {
        private List<CollaboratorRef> agentTools = new ArrayList<>();
        private List<CollaboratorRef> handoffs = new ArrayList<>();
    }

    @Data
    public static class Guardrails {
        private List<GuardrailRef> input = new ArrayList<>();
        private List<GuardrailRef> output = new ArrayList<>();
    }

    @Data
    public static class GuardrailRef {
        @NotBlank
        private String ref;
        private String execution;
    }

    @Data
    public static class Output {
        private String mode = "text";
        private String workflowRef;
        private Map<String, Object> schema = new LinkedHashMap<>();
    }

    @Data
    public static class RuntimeDefaults {
        private Integer maxTurns = 8;
        private Integer timeoutMs = 60_000;
        private Integer maxAgentDepth = 4;
        private Integer toolConcurrency = 4;
        private String stateStrategy = "applicationReplay";
        private Tracing tracing = new Tracing();
    }

    @Data
    public static class Tracing {
        private Boolean enabled = Boolean.TRUE;
        private Boolean includeSensitiveData = Boolean.FALSE;
        private String workflowName;
    }
}
