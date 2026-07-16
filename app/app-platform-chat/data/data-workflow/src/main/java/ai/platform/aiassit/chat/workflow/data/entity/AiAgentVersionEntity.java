package ai.platform.aiassit.chat.workflow.data.entity;

import ai.platform.aiassit.chat.workflow.data.enums.DefinitionStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

import java.time.LocalDateTime;

/** Immutable, runtime-neutral Agent manifest version. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_chat_agent_version", autoResultMap = true)
public class AiAgentVersionEntity extends LogicalDeleteEntity {

    @JdbcColumn(name = "agent_code", dataType = "VARCHAR(64)", length = 64, nullable = false,
            comment = "Agent 编码")
    @TableField("agent_code")
    private String agentCode;

    @JdbcColumn(name = "version_no", dataType = "INT", nullable = false, comment = "版本号")
    @TableField("version_no")
    private Integer versionNo;

    @JdbcColumn(name = "status", dataType = "INT", nullable = false,
            comment = "版本状态：1=草稿,2=已校验,3=已发布,4=已归档")
    @TableField(value = "status", typeHandler = DefaultEnumTypeHandler.class)
    private DefinitionStatus status = DefinitionStatus.DRAFT;

    @JdbcColumn(name = "manifest_json", dataType = "MEDIUMTEXT", nullable = false,
            comment = "完整中立 Agent Manifest JSON")
    @TableField("manifest_json")
    private String manifestJson;

    @JdbcColumn(name = "validation_json", dataType = "MEDIUMTEXT", nullable = true,
            comment = "最近一次校验报告 JSON")
    @TableField("validation_json")
    private String validationJson;

    @JdbcColumn(name = "checksum", dataType = "CHAR(64)", length = 64, nullable = false,
            comment = "Manifest SHA-256")
    @TableField("checksum")
    private String checksum;

    @JdbcColumn(name = "published_at", dataType = "DATETIME", nullable = true,
            comment = "发布时间")
    @TableField("published_at")
    private LocalDateTime publishedAt;
}
