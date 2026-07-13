package ai.platform.aiassit.data.virtualization.data.entity;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.TransformMode;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vd_field_transform_rule", autoResultMap = true)
public class FieldTransformRuleEntity extends AuditableEntity {
    @TableField("binding_id") private Long bindingId;
    @TableField("rule_code") private String ruleCode;
    @TableField("rule_name") private String ruleName;
    @TableField(value = "transform_mode", typeHandler = DefaultEnumTypeHandler.class) private TransformMode transformMode;
    @TableField("read_transformer_code") private String readTransformerCode;
    @TableField("read_transformer_version") private Integer readTransformerVersion;
    @TableField("write_transformer_code") private String writeTransformerCode;
    @TableField("write_transformer_version") private Integer writeTransformerVersion;
    @TableField(value = "read_config", typeHandler = JacksonTypeHandler.class) private Map<String, Object> readConfig = new LinkedHashMap<>();
    @TableField(value = "write_config", typeHandler = JacksonTypeHandler.class) private Map<String, Object> writeConfig = new LinkedHashMap<>();
    @TableField("enabled") private Boolean enabled;
    @TableField("remark") private String remark;
}
