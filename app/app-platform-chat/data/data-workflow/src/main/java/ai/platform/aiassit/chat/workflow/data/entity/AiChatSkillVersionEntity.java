package ai.platform.aiassit.chat.workflow.data.entity;

import ai.platform.aiassit.chat.workflow.data.enums.DefinitionStatus;
import ai.platform.aiassit.chat.workflow.data.enums.SkillSourceType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

import java.time.LocalDateTime;

/** Version metadata for a form-authored or imported Skill package. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_chat_skill_version", autoResultMap = true)
public class AiChatSkillVersionEntity extends LogicalDeleteEntity {

    @JdbcColumn(name = "skill_code", dataType = "VARCHAR(64)", length = 64, nullable = false,
            comment = "Skill 编码")
    @TableField("skill_code")
    private String skillCode;

    @JdbcColumn(name = "version_no", dataType = "INT", nullable = false, comment = "版本号")
    @TableField("version_no")
    private Integer versionNo;

    @JdbcColumn(name = "status", dataType = "INT", nullable = false,
            comment = "版本状态：1=草稿,2=已校验,3=已发布,4=已归档")
    @TableField(value = "status", typeHandler = DefaultEnumTypeHandler.class)
    private DefinitionStatus status = DefinitionStatus.DRAFT;

    @JdbcColumn(name = "source_type", dataType = "INT", nullable = false,
            comment = "来源：1=表单,2=ZIP")
    @TableField(value = "source_type", typeHandler = DefaultEnumTypeHandler.class)
    private SkillSourceType sourceType;

    @JdbcColumn(name = "entrypoint", dataType = "VARCHAR(512)", length = 512, nullable = false,
            comment = "Skill 入口文件路径")
    @TableField("entrypoint")
    private String entrypoint;

    @JdbcColumn(name = "manifest_json", dataType = "MEDIUMTEXT", nullable = false,
            comment = "Skill 包文件清单与兼容性 JSON")
    @TableField("manifest_json")
    private String manifestJson;

    @JdbcColumn(name = "validation_json", dataType = "MEDIUMTEXT", nullable = true,
            comment = "最近一次校验报告 JSON")
    @TableField("validation_json")
    private String validationJson;

    @JdbcColumn(name = "package_checksum", dataType = "CHAR(64)", length = 64, nullable = false,
            comment = "规范化包内容 SHA-256")
    @TableField("package_checksum")
    private String packageChecksum;

    @JdbcColumn(name = "package_size", dataType = "BIGINT", nullable = false,
            comment = "解压后总字节数")
    @TableField("package_size")
    private Long packageSize;

    @JdbcColumn(name = "published_at", dataType = "DATETIME", nullable = true, comment = "发布时间")
    @TableField("published_at")
    private LocalDateTime publishedAt;
}
