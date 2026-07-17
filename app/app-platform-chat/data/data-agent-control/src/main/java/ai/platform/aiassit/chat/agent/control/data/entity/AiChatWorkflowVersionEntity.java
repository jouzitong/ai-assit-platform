package ai.platform.aiassit.chat.agent.control.data.entity;

import ai.platform.aiassit.chat.agent.control.data.enums.DefinitionStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

import java.time.LocalDateTime;

/** Versioned artifact/output contract. It intentionally contains no executable node graph. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "agent_workflow_version", autoResultMap = true)
public class AiChatWorkflowVersionEntity extends LogicalDeleteEntity {

    @JdbcColumn(name = "workflow_code", dataType = "VARCHAR(64)", length = 64, nullable = false,
            comment = "Workflow 编码")
    @TableField("workflow_code")
    private String workflowCode;

    @JdbcColumn(name = "version_no", dataType = "INT", nullable = false, comment = "版本号")
    @TableField("version_no")
    private Integer versionNo;

    @JdbcColumn(name = "status", dataType = "INT", nullable = false,
            comment = "版本状态：1=草稿,2=已校验,3=已发布,4=已归档")
    @TableField(value = "status", typeHandler = DefaultEnumTypeHandler.class)
    private DefinitionStatus status = DefinitionStatus.DRAFT;

    @JdbcColumn(name = "specification_json", dataType = "MEDIUMTEXT", nullable = false,
            comment = "产出物、检查器和完成策略 JSON")
    @TableField("specification_json")
    private String specificationJson;

    @JdbcColumn(name = "validation_json", dataType = "MEDIUMTEXT", nullable = true,
            comment = "最近一次校验报告 JSON")
    @TableField("validation_json")
    private String validationJson;

    @JdbcColumn(name = "checksum", dataType = "CHAR(64)", length = 64, nullable = false,
            comment = "规范 SHA-256")
    @TableField("checksum")
    private String checksum;

    @JdbcColumn(name = "published_at", dataType = "DATETIME", nullable = true, comment = "发布时间")
    @TableField("published_at")
    private LocalDateTime publishedAt;
}
