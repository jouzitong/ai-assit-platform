package ai.platform.aiassit.chat.agent.control.data.entity;

import ai.platform.aiassit.chat.agent.control.data.enums.DefinitionStatus;
import ai.platform.aiassit.chat.agent.control.data.enums.ToolAdapterType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

import java.time.LocalDateTime;

/** Immutable Tool contract and adapter configuration version. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "agent_tool_version", autoResultMap = true)
public class AiChatToolVersionEntity extends LogicalDeleteEntity {

    @JdbcColumn(name = "tool_code", dataType = "VARCHAR(64)", length = 64, nullable = false,
            comment = "Tool 编码")
    @TableField("tool_code")
    private String toolCode;

    @JdbcColumn(name = "version_no", dataType = "INT", nullable = false, comment = "版本号")
    @TableField("version_no")
    private Integer versionNo;

    @JdbcColumn(name = "status", dataType = "INT", nullable = false,
            comment = "版本状态：1=草稿,2=已校验,3=已发布,4=已归档")
    @TableField(value = "status", typeHandler = DefaultEnumTypeHandler.class)
    private DefinitionStatus status = DefinitionStatus.DRAFT;

    @JdbcColumn(name = "adapter_type", dataType = "INT", nullable = false,
            comment = "适配器：1=FUNCTION,2=HTTP,3=MCP,4=SCRIPT")
    @TableField(value = "adapter_type", typeHandler = DefaultEnumTypeHandler.class)
    private ToolAdapterType adapterType;

    @JdbcColumn(name = "definition_json", dataType = "MEDIUMTEXT", nullable = false,
            comment = "输入输出 Schema、权限、超时和适配配置 JSON")
    @TableField("definition_json")
    private String definitionJson;

    @JdbcColumn(name = "validation_json", dataType = "MEDIUMTEXT", nullable = true,
            comment = "最近一次校验报告 JSON")
    @TableField("validation_json")
    private String validationJson;

    @JdbcColumn(name = "checksum", dataType = "CHAR(64)", length = 64, nullable = false,
            comment = "定义 SHA-256")
    @TableField("checksum")
    private String checksum;

    @JdbcColumn(name = "published_at", dataType = "DATETIME", nullable = true, comment = "发布时间")
    @TableField("published_at")
    private LocalDateTime publishedAt;
}
