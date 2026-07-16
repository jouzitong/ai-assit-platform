package ai.platform.aiassit.chat.workflow.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;

/** Immutable original ZIP bytes retained separately from the file index. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_skill_package")
public class AiChatSkillPackageEntity extends LogicalDeleteEntity {

    @TableField("skill_version_id")
    private Long skillVersionId;

    @TableField("original_filename")
    private String originalFilename;

    @TableField("package_checksum")
    private String packageChecksum;

    @TableField("compressed_size")
    private Long compressedSize;

    @TableField("content")
    private byte[] content;
}
