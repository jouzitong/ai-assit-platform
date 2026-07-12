package ai.platform.aiassit.knowledge.manage.entity;

import ai.platform.aiassit.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassit.service.ai.api.enums.AiKbContentFormat;
import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentStatus;
import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentType;
import ai.platform.aiassit.service.ai.api.enums.AiKbProviderSyncStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识库当前文档实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_kb_document", autoResultMap = true)
public class AiKbDocumentEntity extends AuditableEntity {

    /**
     * 所属知识库编码。
     */
    @JdbcColumn(
            name = "kb_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            comment = "所属知识库编码"
    )
    @TableField("kb_code")
    private String kbCode;

    /**
     * 文档编码，建议使用 sourceKey/tableName。
     */
    @JdbcColumn(
            name = "document_code",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "文档编码，建议使用 sourceKey/tableName"
    )
    @TableField("document_code")
    private String documentCode;

    /**
     * 文档名称。
     */
    @JdbcColumn(
            name = "document_name",
            dataType = "VARCHAR(256)",
            length = 256,
            nullable = false,
            comment = "文档名称"
    )
    @TableField("document_name")
    private String documentName;

    /**
     * 文档类型，例如 DB_TABLE。
     */
    @JdbcColumn(
            name = "document_type",
            dataType = "INT",
            nullable = false,
            comment = "文档类型枚举编码：1=DB_TABLE"
    )
    @TableField("document_type")
    private AiKbDocumentType documentType;

    /**
     * 业务类型。
     */
    @JdbcColumn(
            name = "biz_type",
            dataType = "INT",
            nullable = false,
            comment = "业务类型枚举编码：1=DB_DATA_SOURCE"
    )
    @TableField("biz_type")
    private AiKbBizType bizType;

    /**
     * 业务唯一键。
     */
    @JdbcColumn(
            name = "biz_key",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "业务唯一键"
    )
    @TableField("biz_key")
    private String bizKey;

    /**
     * 文档状态，例如 ACTIVE、DISABLED。
     */
    @JdbcColumn(
            name = "status",
            dataType = "INT",
            nullable = false,
            defaultValue = "1",
            comment = "文档状态枚举编码：1=ACTIVE,2=DISABLED"
    )
    @TableField("status")
    private AiKbDocumentStatus status;

    /**
     * AI 侧远端文档 ID。
     */
    @JdbcColumn(
            name = "provider_document_id",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = true,
            comment = "AI 侧远端文档 ID"
    )
    @TableField("provider_document_id")
    private String providerDocumentId;

    /**
     * AI 侧同步状态。
     */
    @JdbcColumn(
            name = "provider_sync_status",
            dataType = "INT",
            nullable = false,
            defaultValue = "1",
            comment = "AI 侧同步状态枚举编码：1=PENDING,2=RUNNING,3=SUCCESS,4=FAILED"
    )
    @TableField("provider_sync_status")
    private AiKbProviderSyncStatus providerSyncStatus;

    /**
     * 当前文档版本号。
     */
    @JdbcColumn(
            name = "document_version_no",
            dataType = "INT",
            nullable = false,
            defaultValue = "1",
            comment = "当前文档版本号"
    )
    @TableField("document_version_no")
    private Integer documentVersionNo;

    /**
     * 文档内容校验摘要。
     */
    @JdbcColumn(
            name = "content_checksum",
            dataType = "CHAR(64)",
            length = 64,
            nullable = true,
            comment = "文档内容校验摘要，SHA-256"
    )
    @TableField("content_checksum")
    private String contentChecksum;

    /**
     * 文档内容格式，例如 MARKDOWN。
     */
    @JdbcColumn(
            name = "content_format",
            dataType = "INT",
            nullable = false,
            defaultValue = "1",
            comment = "内容格式枚举编码：1=MARKDOWN,2=TEXT,3=JSON"
    )
    @TableField("content_format")
    private AiKbContentFormat contentFormat;

    /**
     * 文档内容大小，单位字节。
     */
    @JdbcColumn(
            name = "content_size",
            dataType = "BIGINT",
            nullable = false,
            defaultValue = "0",
            comment = "文档内容大小，单位字节"
    )
    @TableField("content_size")
    private Long contentSize;

    /**
     * 文档扩展元数据。
     */
    @JdbcColumn(
            name = "meta_json",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "文档扩展元数据 JSON"
    )
    @TableField(value = "meta_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metaJson;

    /**
     * 最近一次生成时间。
     */
    @JdbcColumn(
            name = "last_generated_at",
            dataType = "DATETIME",
            nullable = true,
            comment = "最近一次生成时间"
    )
    @TableField("last_generated_at")
    private LocalDateTime lastGeneratedAt;

    /**
     * 最近一次错误信息。
     */
    @JdbcColumn(
            name = "last_error",
            dataType = "VARCHAR(1024)",
            length = 1024,
            nullable = true,
            comment = "最近一次错误信息"
    )
    @TableField("last_error")
    private String lastError;

    /**
     * 备注。
     */
    @JdbcColumn(
            name = "remark",
            dataType = "VARCHAR(512)",
            length = 512,
            nullable = true,
            comment = "备注"
    )
    @TableField("remark")
    private String remark;
}
