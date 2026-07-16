package ai.platform.aiassit.chat.workflow.data.importer;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** In-memory result of bounded ZIP inspection; package bytes are never written to a filesystem. */
@Data
public class InspectedSkillPackage {

    private boolean valid;
    private String packageRoot;
    private String skillName;
    private String description;
    private String license;
    private String compatibility;
    private byte[] originalPackage;
    private String entrypoint;
    private String checksum;
    private long totalSize;
    private List<File> files = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class File {
        private String path;
        private String mediaType;
        private long size;
        private String checksum;
        private byte[] content;
    }
}
