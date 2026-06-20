package ai.platform.aiassist.service.ai.kb.entity;

import ai.platform.aiassist.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassist.service.ai.api.enums.AiKbContentFormat;
import ai.platform.aiassist.service.ai.api.enums.AiKbDocumentStatus;
import ai.platform.aiassist.service.ai.api.enums.AiKbDocumentType;
import ai.platform.aiassist.service.ai.api.enums.AiKbReviewStatus;
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
 * 知识库文档草稿实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_kb_document", autoResultMap = true)
public class AiKbDocumentEntity extends AuditableEntity {

    /** 所属知识库编码。 */
    @TableField("kb_code")
    private String kbCode;

    /** 所属知识库草稿版本 ID。 */
    @TableField("kb_version_id")
    private Long kbVersionId;

    /** 文档编码，建议使用 sourceKey/tableName。 */
    @TableField("document_code")
    private String documentCode;

    /** 文档名称。 */
    @TableField("document_name")
    private String documentName;

    /** 文档类型，例如 DB_TABLE。 */
    @TableField("document_type")
    private AiKbDocumentType documentType;

    /** 业务类型。 */
    @TableField("biz_type")
    private AiKbBizType bizType;

    /** 业务唯一键。 */
    @TableField("biz_key")
    private String bizKey;

    /** 来源系统，例如 db-engine。 */
    @TableField("source_system")
    private String sourceSystem;

    /** 文档状态，例如 ACTIVE、DISABLED。 */
    @TableField("status")
    private AiKbDocumentStatus status;

    /** 当前草稿版本号。 */
    @TableField("draft_version_no")
    private Integer draftVersionNo;

    /** 文档内容校验摘要。 */
    @TableField("content_checksum")
    private String contentChecksum;

    /** 文档内容格式，例如 MARKDOWN。 */
    @TableField("content_format")
    private AiKbContentFormat contentFormat;

    /** 文档内容大小，单位字节。 */
    @TableField("content_size")
    private Long contentSize;

    /** 文档扩展元数据。 */
    @TableField(value = "meta_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metaJson;

    /** 审核状态，例如 DRAFT、READY、REJECTED、PUBLISHED。 */
    @TableField("review_status")
    private AiKbReviewStatus reviewStatus;

    /** 最近一次生成时间。 */
    @TableField("last_generated_at")
    private LocalDateTime lastGeneratedAt;

    /** 最近一次错误信息。 */
    @TableField("last_error")
    private String lastError;

    /** 备注。 */
    @TableField("remark")
    private String remark;
}
