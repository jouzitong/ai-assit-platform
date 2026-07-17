package ai.platform.aiassit.chat.agent.control.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;

/** A safely inspected immutable file inside a Skill version. Files are never extracted to the server filesystem. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_skill_file")
public class AiChatSkillFileEntity extends LogicalDeleteEntity {

    @JdbcColumn(name = "skill_version_id", dataType = "BIGINT", nullable = false,
            comment = "Skill 版本主键")
    @TableField("skill_version_id")
    private Long skillVersionId;

    @JdbcColumn(name = "path", dataType = "VARCHAR(512)", length = 512, nullable = false,
            comment = "规范化包内相对路径")
    @TableField("path")
    private String path;

    @JdbcColumn(name = "media_type", dataType = "VARCHAR(128)", length = 128, nullable = true,
            comment = "推断的媒体类型")
    @TableField("media_type")
    private String mediaType;

    @JdbcColumn(name = "content_size", dataType = "BIGINT", nullable = false, comment = "文件字节数")
    @TableField("content_size")
    private Long contentSize;

    @JdbcColumn(name = "checksum", dataType = "CHAR(64)", length = 64, nullable = false,
            comment = "文件 SHA-256")
    @TableField("checksum")
    private String checksum;

    @JdbcColumn(name = "content", dataType = "LONGBLOB", nullable = false, comment = "文件内容")
    @TableField("content")
    private byte[] content;
}
