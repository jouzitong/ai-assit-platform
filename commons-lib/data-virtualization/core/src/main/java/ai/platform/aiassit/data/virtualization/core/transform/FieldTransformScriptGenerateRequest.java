package ai.platform.aiassit.data.virtualization.core.transform;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FieldTransformScriptGenerateRequest {
    private Long entityId;
    private Long bindingId;
    private String ruleName;
    private String requirement;
    private String currentScript;
    private List<FieldContext> physicalFields = new ArrayList<>();
    private List<FieldContext> virtualFields = new ArrayList<>();
    private List<MappingContext> mappings = new ArrayList<>();

    @Data
    public static class FieldContext {
        private String code;
        private String name;
        private String dataType;
        private Boolean nullable;
        private Boolean primaryKey;
        private String remark;
    }

    @Data
    public static class MappingContext {
        private String side;
        private String code;
        private String name;
        private String dataType;
        private Boolean requiredOnWrite;
    }
}
