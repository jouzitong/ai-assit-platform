package ai.platform.aiassist.service.ai.kb.entity;

import ai.platform.aiassist.service.ai.api.enums.AiKbChangeType;
import ai.platform.aiassist.service.ai.api.enums.AiKbContentFormat;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识库文档发布快照实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_kb_document_version", autoResultMap = true)
public class AiKbDocumentVersionEntity extends AuditableEntity {

    /** 所属知识库编码。 */
    @TableField("kb_code")
    private String kbCode;

    /** 文档编码。 */
    @TableField("document_code")
    private String documentCode;

    /** 所属知识库版本 ID。 */
    @TableField("kb_version_id")
    private Long kbVersionId;

    /** 知识库版本号。 */
    @TableField("version_no")
    private Integer versionNo;

    /** 文档自身版本号。 */
    @TableField("document_version_no")
    private Integer documentVersionNo;

    /** 变更类型，例如 CREATE、UPDATE、DELETE。 */
    @TableField("change_type")
    private AiKbChangeType changeType;

    /** 文档内容校验摘要。 */
    @TableField("content_checksum")
    private String contentChecksum;

    /** 发布时的文档内容格式，例如 MARKDOWN。 */
    @TableField("content_format")
    private AiKbContentFormat contentFormat;

    /** 发布时的文档内容大小，单位字节。 */
    @TableField("content_size")
    private Long contentSize;

    /** 发布时的扩展元数据快照。 */
    @TableField(value = "meta_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metaJson;

    /** 来源系统。 */
    @TableField("source_system")
    private String sourceSystem;

    /** 发布时间。 */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /** 发布人。 */
    @TableField("published_by")
    private String publishedBy;

    /** 备注。 */
    @TableField("remark")
    private String remark;
}
