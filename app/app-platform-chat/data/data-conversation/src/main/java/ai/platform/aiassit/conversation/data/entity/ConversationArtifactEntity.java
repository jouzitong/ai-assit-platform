package ai.platform.aiassit.conversation.data.entity;

import ai.platform.aiassit.conversation.data.enums.ConversationArtifactType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

/**
 * 会话中生成的非文本产物实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "conversation_artifact", autoResultMap = true)
public class ConversationArtifactEntity extends LogicalDeleteEntity {

    /**
     * 工件的唯一代码。
     */
    @JdbcColumn(
            name = "artifact_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "产物编码"
    )
    @TableField("artifact_code")
    private String artifactCode;

    /**
     * 产物所属轮次编码。
     *
     * @see ConversationRoundEntity#getRoundCode() roundCode
     */
    @JdbcColumn(
            name = "round_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            comment = "轮次编码"
    )
    @TableField("round_code")
    private String roundCode;

    /**
     * 产物类型：文件、图片或 Render JSON。
     */
    @JdbcColumn(
            name = "artifact_type",
            dataType = "INT",
            nullable = false,
            comment = "产物类型：1=文件,2=图片,3=Render JSON"
    )
    @TableField(value = "artifact_type", typeHandler = DefaultEnumTypeHandler.class)
    private ConversationArtifactType artifactType;

    /**
     * 生成工件的会话阶段。
     */
    @JdbcColumn(
            name = "stage",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            comment = "流程阶段"
    )
    @TableField("stage")
    private String stage;

    /**
     * 工件的标题。
     */
    @JdbcColumn(
            name = "title",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = true,
            comment = "产物标题"
    )
    @TableField("title")
    private String title;

    /**
     * 工件的内容。
     */
    @JdbcColumn(
            name = "content",
            dataType = "MEDIUMTEXT",
            nullable = false,
            comment = "产物内容"
    )
    @TableField("content")
    private String content;

    /**
     * 内容的格式（例如，JSON、纯文本）。
     */
    @JdbcColumn(
            name = "content_format",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            defaultValue = "'JSON'",
            comment = "内容格式"
    )
    @TableField("content_format")
    private String contentFormat;

    /**
     * 工件在会话中的序列号。
     */
    @JdbcColumn(
            name = "seq_no",
            dataType = "INT",
            nullable = false,
            defaultValue = "1",
            comment = "轮次内顺序"
    )
    @TableField("seq_no")
    private Integer seqNo;

    /**
     * 工件的附加JSON数据。
     */
    @JdbcColumn(
            name = "ext_json",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "扩展信息"
    )
    @TableField("ext_json")
    private String extJson;
}
