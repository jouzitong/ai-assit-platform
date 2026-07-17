package ai.platform.aiassit.knowledge.manage.entity.store;

import ai.platform.aiassit.service.ai.api.dto.AiKbAuthConfig;
import ai.platform.aiassit.service.ai.api.enums.AiKbStoreSyncStatus;
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
     * 本地知识库业务编码，由用户自定义，并应与 {@link #kbName} 语义相近。
     *
     * <p>该编码是 Agent、应用和内部调用使用的稳定标识；RAGFlow Dataset ID
     * 单独保存在 {@link #providerKbId}，不得向调用方透出或作为知识库编码使用。</p>
     */
    @JdbcColumn(
            name = "kb_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "用户自定义的知识库业务编码"
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
     * RAGFlow 侧真实 Dataset ID，仅供 Provider 适配调用。
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

    /** Dataset 描述。 */
    @JdbcColumn(
            name = "description",
            dataType = "VARCHAR(512)",
            length = 512,
            nullable = true,
            comment = "RAGFlow Dataset 描述"
    )
    @TableField("description")
    private String description;

    /** 向量化模型标识。 */
    @JdbcColumn(
            name = "embedding_model",
            dataType = "VARCHAR(256)",
            length = 256,
            nullable = true,
            comment = "RAGFlow embedding 模型"
    )
    @TableField("embedding_model")
    private String embeddingModel;

    /** Dataset 权限，例如 team/private。 */
    @JdbcColumn(
            name = "permission",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = true,
            comment = "RAGFlow Dataset 权限"
    )
    @TableField("permission")
    private String permission;

    /** RAGFlow 内置分片方式。 */
    @JdbcColumn(
            name = "chunk_method",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "RAGFlow 分片方式"
    )
    @TableField("chunk_method")
    private String chunkMethod;

    /** RAGFlow 内置分片配置。 */
    @JdbcColumn(
            name = "parser_config_json",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "RAGFlow parser_config JSON"
    )
    @TableField(value = "parser_config_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> parserConfig;

    /** 自定义解析方式。 */
    @JdbcColumn(
            name = "parse_type",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "RAGFlow 自定义解析类型"
    )
    @TableField("parse_type")
    private String parseType;

    /** 自定义 ingestion pipeline 标识。 */
    @JdbcColumn(
            name = "pipeline_id",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = true,
            comment = "RAGFlow ingestion pipeline ID"
    )
    @TableField("pipeline_id")
    private String pipelineId;

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
     * 与 RAGFlow Dataset 的同步状态。
     */
    @JdbcColumn(
            name = "sync_status",
            dataType = "INT",
            nullable = false,
            defaultValue = "2",
            comment = "RAGFlow 同步状态：1=创建中,2=已同步,3=创建失败,4=更新中,5=更新失败,6=删除中,7=删除失败"
    )
    @TableField("sync_status")
    private AiKbStoreSyncStatus syncStatus;

    /**
     * 最近一次同步错误。
     */
    @JdbcColumn(
            name = "sync_error",
            dataType = "VARCHAR(1024)",
            length = 1024,
            nullable = true,
            comment = "最近一次 RAGFlow 同步错误"
    )
    @TableField("sync_error")
    private String syncError;

    /**
     * 最近一次同步时间。
     */
    @JdbcColumn(
            name = "last_sync_at",
            dataType = "DATETIME",
            nullable = true,
            comment = "最近一次 RAGFlow 同步时间"
    )
    @TableField("last_sync_at")
    private LocalDateTime lastSyncAt;

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
     * 创建或更新时从系统参数同步的认证快照；不向管理页面返回明文。
     */
    @JdbcColumn(
            name = "auth_json",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "知识库 Provider 认证配置 JSON 快照"
    )
    @TableField(value = "auth_json", typeHandler = JacksonTypeHandler.class)
    private AiKbAuthConfig auth;

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
