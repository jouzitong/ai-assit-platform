package ai.platform.aiassist.service.ai.kb.entity;

import ai.platform.aiassist.service.ai.api.enums.AiKbVersionStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

import java.time.LocalDateTime;

/**
 * 知识库版本记录实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_kb_version", autoResultMap = true)
public class AiKbVersionEntity extends AuditableEntity {

    /** 所属知识库编码。 */
    @TableField("kb_code")
    private String kbCode;

    /** 版本号。 */
    @TableField("version_no")
    private Integer versionNo;

    /** 版本名称。 */
    @TableField("version_name")
    private String versionName;

    /** 版本状态，例如 DRAFT、CURRENT、HISTORY、DISCARDED。 */
    @TableField("status")
    private AiKbVersionStatus status;

    /** 发布时间。 */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /** 发布人 ID。 */
    @TableField("published_by")
    private Long publishedBy;

    /** 备注。 */
    @TableField("remark")
    private String remark;
}
