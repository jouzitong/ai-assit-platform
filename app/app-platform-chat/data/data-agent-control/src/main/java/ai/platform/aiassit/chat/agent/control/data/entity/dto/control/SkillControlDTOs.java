package ai.platform.aiassit.chat.agent.control.data.entity.dto.control;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API contracts for form-authored and quarantined ZIP Skill packages. */
public final class SkillControlDTOs {

    private SkillControlDTOs() {
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
    public static class FormDraftRequest {
        private String code;
        private String name;
        private String description;
        private String license;
        private String compatibility;
        /** Markdown body excluding YAML frontmatter. */
        private String content;
        /** Legacy alias for content. */
        private String instructions;
        private List<String> toolRefs = new ArrayList<>();
        private List<String> compatibleRuntimes = new ArrayList<>();
        private Boolean enabled = Boolean.TRUE;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UpdateRequest extends FormDraftRequest {
        private String sourceType;
    }

    @Data
    public static class ImportRequest {
        private String code;
        private String name;
        private String description;
        private List<String> toolRefs = new ArrayList<>();
        private List<String> compatibleRuntimes = new ArrayList<>();
        private Boolean enabled = Boolean.TRUE;
    }

    @Data
    public static class Version {
        private Long id;
        private String skillCode;
        private String code;
        private String name;
        private String description;
        private Boolean enabled;
        private Integer version;
        private Integer currentPublishedVersion;
        private Integer draftVersion;
        private String sourceType;
        private String status;
        private String entrypoint;
        private String license;
        private String compatibility;
        private String content;
        private List<String> toolRefs = new ArrayList<>();
        private List<String> compatibleRuntimes = new ArrayList<>();
        private Map<String, Object> manifest = new LinkedHashMap<>();
        private List<FileItem> files = new ArrayList<>();
        private ValidationReportDTO validation;
        private String checksum;
        private String packageSha256;
        private Long packageSize;
        private LocalDateTime publishedAt;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class Inspection {
        private String draftId;
        private boolean valid;
        private String entrypoint;
        private String checksum;
        private long totalSize;
        private Map<String, Object> manifest = new LinkedHashMap<>();
        private Map<String, Object> skill = new LinkedHashMap<>();
        private List<FileItem> files = new ArrayList<>();
        private ValidationReportDTO compatibility;
        private List<ValidationReportDTO.Issue> risks = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    public static class FileItem {
        private String name;
        private String path;
        private String role;
        private String mediaType;
        private long size;
        private String checksum;
    }
}
