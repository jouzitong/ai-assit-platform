package ai.platform.aiassit.knowledge.manage.entity;

import ai.platform.aiassit.service.ai.api.enums.AiKbContentFormat;
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

import java.util.Map;

/**
 * 知识库发布快照正文实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_kb_document_version_content", autoResultMap = true)
public class AiKbDocumentVersionContentEntity extends AuditableEntity {

    /** 所属文档版本快照 ID。 */
    @JdbcColumn(name = "document_version_id", unique = true, comment = "所属文档版本快照 ID。")
    @TableField("document_version_id")
    private Long documentVersionId;

    /** 内容格式，例如 MARKDOWN、TEXT、JSON。 */
    @JdbcColumn(name = "content_format", comment = "内容格式，例如 MARKDOWN、TEXT、JSON。")
    @TableField("content_format")
    private AiKbContentFormat contentFormat;

    /** 内容大小，单位字节。 */
    @JdbcColumn(name = "content_size", comment = "内容大小，单位字节。")
    @TableField("content_size")
    private Long contentSize;

    /** 发布快照的结构化内容。 */
    @JdbcColumn(name = "content_json", comment = "发布快照的结构化内容。")
    @TableField(value = "content_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> contentJson;

    /** 发布快照的最终文本内容。 */
    @JdbcColumn(name = "rendered_content", comment = "发布快照的最终文本内容。")
    @TableField("rendered_content")
    private String renderedContent;

    /** 正文扩展信息。 */
    @JdbcColumn(name = "ext_json", comment = "正文扩展信息。")
    @TableField(value = "ext_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extJson;
}
