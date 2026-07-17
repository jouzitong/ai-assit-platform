package ai.platform.aiassit.chat.agent.control.data.entity;

import ai.platform.aiassit.chat.agent.control.data.enums.DefinitionStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

/** Agent catalog entry. Versioned executable content lives in {@code agent_definition_version}. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "agent_definition", autoResultMap = true)
public class AiAgentEntity extends LogicalDeleteEntity {

    @JdbcColumn(name = "code", dataType = "VARCHAR(64)", length = 64, nullable = false, unique = true,
            comment = "Agent 编码")
    @TableField("code")
    private String code;

    @JdbcColumn(name = "name", dataType = "VARCHAR(128)", length = 128, nullable = false,
            comment = "Agent 名称")
    @TableField("name")
    private String name;

    @JdbcColumn(name = "description", dataType = "VARCHAR(512)", length = 512, nullable = true,
            comment = "Agent 说明")
    @TableField("description")
    private String description;

    @JdbcColumn(name = "current_version", dataType = "INT", nullable = true,
            comment = "当前发布版本号")
    @TableField("current_version")
    private Integer currentVersion;

    @JdbcColumn(name = "status", dataType = "INT", nullable = false,
            comment = "目录状态：1=草稿,2=已校验,3=已发布,4=已归档")
    @TableField(value = "status", typeHandler = DefaultEnumTypeHandler.class)
    private DefinitionStatus status = DefinitionStatus.DRAFT;

    @JdbcColumn(name = "enabled", dataType = "BOOLEAN", nullable = false, defaultValue = "TRUE",
            comment = "是否启用")
    @TableField("enabled")
    private Boolean enabled = Boolean.TRUE;
}
