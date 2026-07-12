package ai.platform.aiassit.knowledge.manage.entity.document;

import ai.platform.aiassit.service.ai.api.enums.AiKbChangeType;
import ai.platform.aiassit.service.ai.api.enums.AiKbContentFormat;
import ai.platform.aiassit.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentType;
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
 * 知识库文档历史版本快照实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_kb_document_version", autoResultMap = true)
public class AiKbDocumentVersionEntity extends AuditableEntity {

    /** 所属知识库编码。 */
    @JdbcColumn(
            name = "kb_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            comment = "所属知识库编码"
    )
    @TableField("kb_code")
    private String kbCode;

    /** 文档编码。 */
    @JdbcColumn(
            name = "document_code",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "文档编码"
    )
    @TableField("document_code")
    private String documentCode;

    /** 文档名称。 */
    @JdbcColumn(
            name = "document_name",
            dataType = "VARCHAR(256)",
            length = 256,
            nullable = false,
            comment = "文档名称"
    )
    @TableField("document_name")
    private String documentName;

    /** 文档类型。 */
    @JdbcColumn(
            name = "document_type",
            dataType = "INT",
            nullable = false,
            comment = "文档类型枚举编码：1=DB_TABLE"
    )
    @TableField("document_type")
    private AiKbDocumentType documentType;

    /** 业务类型。 */
    @JdbcColumn(
            name = "biz_type",
            dataType = "INT",
            nullable = false,
            comment = "业务类型枚举编码：1=DB_DATA_SOURCE"
    )
    @TableField("biz_type")
    private AiKbBizType bizType;

    /** 业务唯一键。 */
    @JdbcColumn(
            name = "biz_key",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "业务唯一键"
    )
    @TableField("biz_key")
    private String bizKey;

    /** 文档自身版本号。 */
    @JdbcColumn(
            name = "document_version_no",
            dataType = "INT",
            nullable = false,
            comment = "文档自身版本号"
    )
    @TableField("document_version_no")
    private Integer documentVersionNo;

    /** 变更类型，例如 CREATE、UPDATE、DELETE。 */
    @JdbcColumn(
            name = "change_type",
            dataType = "INT",
            nullable = false,
            comment = "变更类型枚举编码：1=CREATE,2=UPDATE,3=DELETE"
    )
    @TableField("change_type")
    private AiKbChangeType changeType;

    /** 文档内容校验摘要。 */
    @JdbcColumn(
            name = "content_checksum",
            dataType = "CHAR(64)",
            length = 64,
            nullable = true,
            comment = "文档内容校验摘要，SHA-256"
    )
    @TableField("content_checksum")
    private String contentChecksum;

    /** 发布时的文档内容格式，例如 MARKDOWN。 */
    @JdbcColumn(
            name = "content_format",
            dataType = "INT",
            nullable = false,
            defaultValue = "1",
            comment = "内容格式枚举编码：1=MARKDOWN,2=TEXT,3=JSON"
    )
    @TableField("content_format")
    private AiKbContentFormat contentFormat;

    /** 发布时的文档内容大小，单位字节。 */
    @JdbcColumn(
            name = "content_size",
            dataType = "BIGINT",
            nullable = false,
            defaultValue = "0",
            comment = "发布时文档内容大小，单位字节"
    )
    @TableField("content_size")
    private Long contentSize;

    /** 发布时的扩展元数据快照。 */
    @JdbcColumn(
            name = "meta_json",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "发布时扩展元数据快照 JSON"
    )
    @TableField(value = "meta_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metaJson;

    /** 快照时间。 */
    @JdbcColumn(
            name = "snapshot_at",
            dataType = "DATETIME",
            nullable = true,
            comment = "快照时间"
    )
    @TableField("snapshot_at")
    private LocalDateTime snapshotAt;

    /** 快照创建人。 */
    @JdbcColumn(
            name = "snapshot_by",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "快照创建人"
    )
    @TableField("snapshot_by")
    private String snapshotBy;

    /** 备注。 */
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
