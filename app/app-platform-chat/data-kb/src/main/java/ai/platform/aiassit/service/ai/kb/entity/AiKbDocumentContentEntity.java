package ai.platform.aiassit.service.ai.kb.entity;

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

import java.util.Map;

/**
 * 知识库草稿文档正文实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_kb_document_content", autoResultMap = true)
public class AiKbDocumentContentEntity extends AuditableEntity {

    /** 所属草稿文档 ID。 */
    @TableField("document_id")
    private Long documentId;

    /** 内容格式，例如 MARKDOWN、TEXT、JSON。 */
    @TableField("content_format")
    private AiKbContentFormat contentFormat;

    /** 内容大小，单位字节。 */
    @TableField("content_size")
    private Long contentSize;

    /** 结构化内容。 */
    @TableField(value = "content_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> contentJson;

    /** 渲染后的最终文本内容。 */
    @TableField("rendered_content")
    private String renderedContent;

    /** 正文扩展信息。 */
    @TableField(value = "ext_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extJson;
}
