package ai.platform.aiassit.chat.agent.control.data.entity.dto.control;

import ai.platform.aiassit.chat.agent.control.data.enums.ToolAdapterType;
import jakarta.validation.Valid;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned Tool contracts for platform-managed source and legacy portable bindings. */
public final class ToolControlDTOs {

    private ToolControlDTOs() {
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

    @Data
    public static class DraftRequest {
        private String code;
        private String name;
        private String description;
        private Boolean enabled = Boolean.TRUE;
        private Map<String, Object> inputSchema = new LinkedHashMap<>();
        private Map<String, Object> outputSchema = new LinkedHashMap<>();
        private Map<String, Object> permissionPolicy = new LinkedHashMap<>();
        private Map<String, Object> approvalPolicy = new LinkedHashMap<>();
        private Integer timeoutMs = 30_000;
        private String executionMode;
        private String implementationRuntime = "PYTHON";
        private List<String> compatibleAgentRuntimes = new ArrayList<>(List.of(
                "OPENAI_AGENTS_PYTHON", "OPENAI_AGENTS_TYPESCRIPT"));
        private String sourceCode;
        private Map<String, Object> runtimeConfig = new LinkedHashMap<>();
        @Valid
        private List<Binding> bindings = new ArrayList<>();

        /** Legacy request compatibility; flattened fields above remain canonical. */
        private ToolAdapterType adapterType;
        private Map<String, Object> definition;
    }

    @Data
    public static class Binding {
        private String bindingType;
        private String runtimeType;
        private String endpointRef;
        private String packageUri;
        private String entrypoint;
        private Boolean enabled = Boolean.TRUE;
        private Map<String, Object> config = new LinkedHashMap<>();
    }

    @Data
    public static class Version {
        private Long id;
        private String toolCode;
        private String code;
        private String name;
        private String description;
        private Boolean enabled;
        private Integer version;
        private Integer currentPublishedVersion;
        private Integer draftVersion;
        private String status;
        private String adapterType;
        private Map<String, Object> definition = new LinkedHashMap<>();
        private Map<String, Object> inputSchema = new LinkedHashMap<>();
        private Map<String, Object> outputSchema = new LinkedHashMap<>();
        private Map<String, Object> permissionPolicy = new LinkedHashMap<>();
        private Map<String, Object> approvalPolicy = new LinkedHashMap<>();
        private Integer timeoutMs;
        private String executionMode;
        private String implementationRuntime;
        private List<String> compatibleAgentRuntimes = new ArrayList<>();
        private String sourceCode;
        private Map<String, Object> runtimeConfig = new LinkedHashMap<>();
        private List<Binding> bindings = new ArrayList<>();
        private ValidationReportDTO validation;
        private String checksum;
        private LocalDateTime publishedAt;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }
}
