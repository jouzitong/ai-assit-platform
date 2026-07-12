package ai.platform.aiassit.knowledge.manage.entity;

import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

import java.util.List;
import java.util.Map;

/**
 * 本地知识库主实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_kb_store", autoResultMap = true)
public class AiKbStoreEntity extends AuditableEntity {

    /**
     * 本地知识库编码。
     *
     * @see #providerKbId 与 kbCode 相同
     */
    @JdbcColumn(
            name = "kb_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "本地知识库编码"
    )
    @TableField("kb_code")
    private String kbCode;

    /**
     * 知识库名称。
     */
    @JdbcColumn(
            name = "kb_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "知识库名称"
    )
    @TableField("kb_name")
    private String kbName;

    /**
     * 知识库客户端类型。
     *
     * <p>运行时据此选择知识库客户端 Driver；不表示知识库供应商。</p>
     */
    @JdbcColumn(
            name = "client_type",
            dataType = "INT",
            nullable = false,
            comment = "知识库客户端类型：1=BAILIAN,2=RAWFLOW"
    )
    @TableField(value = "client_type", typeHandler = DefaultEnumTypeHandler.class)
    private AiKnowledgeClientType clientType;

    /**
     * AI 侧真实知识库 ID。
     */
    @JdbcColumn(
            name = "provider_kb_id",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = true,
            comment = "AI 侧真实知识库 ID"
    )
    @TableField("provider_kb_id")
    private String providerKbId;

    /**
     * 是否启用。
     */
    @JdbcColumn(
            name = "enabled",
            dataType = "TINYINT(1)",
            length = 1,
            nullable = false,
            defaultValue = "1",
            comment = "是否启用：1=启用，0=禁用"
    )
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 知识库标签。
     */
    @JdbcColumn(
            name = "tags_json",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "知识库标签 JSON 数组"
    )
    @TableField(value = "tags_json", typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /**
     * 知识库请求地址。
     */
    @JdbcColumn(
            name = "url",
            dataType = "VARCHAR(512)",
            length = 512,
            nullable = true,
            comment = "知识库请求地址"
    )
    @TableField("url")
    private String url;

    /**
     * 扩展信息。
     */
    @JdbcColumn(
            name = "ext_json",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "扩展信息 JSON"
    )
    @TableField(value = "ext_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extJson;
}
