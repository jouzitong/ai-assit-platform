package ai.platform.aiassit.data.virtualization.data.dto;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.TransformMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class FieldTransformRuleDTO extends AuditableDTO {
    private Long bindingId;
    private String ruleCode;
    private String ruleName;
    private TransformMode transformMode;
    private String readTransformerCode;
    private Integer readTransformerVersion;
    private String writeTransformerCode;
    private Integer writeTransformerVersion;
    private Map<String, Object> readConfig = new LinkedHashMap<>();
    private Map<String, Object> writeConfig = new LinkedHashMap<>();
    private Boolean enabled;
    private String remark;
}
