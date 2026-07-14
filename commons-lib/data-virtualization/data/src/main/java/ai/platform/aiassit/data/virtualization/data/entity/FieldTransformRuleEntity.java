package ai.platform.aiassit.data.virtualization.data.entity;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.TransformMode;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vd_field_transform_rule", autoResultMap = true)
public class FieldTransformRuleEntity extends AuditableEntity {
    @JdbcColumn(name = "binding_id", dataType = "BIGINT", nullable = false, comment = "物理绑定ID")
    @TableField("binding_id")
    private Long bindingId;

    @JdbcColumn(name = "rule_code", dataType = "VARCHAR(64)", length = 64, nullable = false, comment = "规则稳定编码")
    @TableField("rule_code")
    private String ruleCode;

    @JdbcColumn(name = "rule_name", dataType = "VARCHAR(128)", length = 128, nullable = false, comment = "规则名称")
    @TableField("rule_name")
    private String ruleName;

    @JdbcColumn(name = "transform_mode", dataType = "INT", nullable = false, comment = "变换模式：0只读，1只写，2双向")
    @TableField(value = "transform_mode", typeHandler = DefaultEnumTypeHandler.class)
    private TransformMode transformMode;

    @JdbcColumn(name = "read_transformer_code", dataType = "VARCHAR(64)", length = 64, nullable = true, comment = "读取变换器编码")
    @TableField("read_transformer_code")
    private String readTransformerCode;

    @JdbcColumn(name = "read_transformer_version", dataType = "INT", nullable = true, comment = "读取变换器版本")
    @TableField("read_transformer_version")
    private Integer readTransformerVersion;

    @JdbcColumn(name = "write_transformer_code", dataType = "VARCHAR(64)", length = 64, nullable = true, comment = "写回变换器编码")
    @TableField("write_transformer_code")
    private String writeTransformerCode;

    @JdbcColumn(name = "write_transformer_version", dataType = "INT", nullable = true, comment = "写回变换器版本")
    @TableField("write_transformer_version")
    private Integer writeTransformerVersion;

    @JdbcColumn(name = "read_config", dataType = "JSON", nullable = true, comment = "强类型读取配置")
    @TableField(value = "read_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> readConfig = new LinkedHashMap<>();

    @JdbcColumn(name = "write_config", dataType = "JSON", nullable = true, comment = "强类型写回配置")
    @TableField(value = "write_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> writeConfig = new LinkedHashMap<>();

    @JdbcColumn(name = "script_code", dataType = "TEXT", nullable = true, comment = "Python-like字段转换脚本")
    @TableField("script_code")
    private String scriptCode;

    @JdbcColumn(name = "enabled", dataType = "BOOLEAN", nullable = false, defaultValue = "TRUE", comment = "是否启用")
    @TableField("enabled")
    private Boolean enabled;

    @JdbcColumn(name = "remark", dataType = "VARCHAR(512)", length = 512, nullable = true, comment = "备注")
    @TableField("remark")
    private String remark;
}
