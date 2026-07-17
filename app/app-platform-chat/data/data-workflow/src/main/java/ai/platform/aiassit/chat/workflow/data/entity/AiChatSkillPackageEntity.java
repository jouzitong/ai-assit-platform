package ai.platform.aiassit.chat.workflow.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;

/** Immutable original ZIP bytes retained separately from the file index. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_skill_package")
public class AiChatSkillPackageEntity extends LogicalDeleteEntity {

    @JdbcColumn(name = "skill_version_id", dataType = "BIGINT", nullable = false,
            comment = "Skill 版本主键")
    @TableField("skill_version_id")
    private Long skillVersionId;

    @JdbcColumn(name = "original_filename", dataType = "VARCHAR(255)", length = 255, nullable = false,
            comment = "原始 ZIP 文件名")
    @TableField("original_filename")
    private String originalFilename;

    @JdbcColumn(name = "package_checksum", dataType = "CHAR(64)", length = 64, nullable = false,
            comment = "原始 ZIP SHA-256")
    @TableField("package_checksum")
    private String packageChecksum;

    @JdbcColumn(name = "compressed_size", dataType = "BIGINT", nullable = false,
            comment = "压缩包字节数")
    @TableField("compressed_size")
    private Long compressedSize;

    @JdbcColumn(name = "content", dataType = "LONGBLOB", nullable = false,
            comment = "原始 ZIP 内容")
    @TableField("content")
    private byte[] content;
}
