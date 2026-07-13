package ai.platform.aiassit.model.entity;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
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

import java.util.Map;

/**
 * AI 模型配置实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_model_config", autoResultMap = true)
public class AiModelConfigEntity extends AuditableEntity {

    @JdbcColumn(name = "client_id", dataType = "BIGINT", nullable = true, comment = "AI客户端配置ID")
    @TableField("client_id")
    private Long clientId;

    /**
     * 模型编码。（内部）
     */
    @JdbcColumn(
            name = "model_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "模型编码"
    )
    @TableField("model_code")
    private String modelCode;
    /**
     * 模型名称。
     */
    @JdbcColumn(
            name = "model_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "模型名称"
    )
    @TableField("model_name")
    private String modelName;
    /**
     * 对话客户端类型。
     *
     * <p>运行时据此选择客户端 Driver；不表示模型供应商。</p>
     */
    @JdbcColumn(
            name = "client_type",
            dataType = "INT",
            nullable = false,
            comment = "对话客户端类型：1=SPRING_AI,2=AI_AGENT"
    )
    @TableField(value = "client_type", typeHandler = DefaultEnumTypeHandler.class)
    private AiChatClientType clientType;
    /**
     * 提供商请求基础地址。
     */
    @JdbcColumn(
            name = "base_url",
            dataType = "VARCHAR(512)",
            length = 512,
            nullable = true,
            comment = "提供商请求基础地址"
    )
    @TableField("base_url")
    private String baseUrl;
    /**
     * 提供商侧模型标识。
     */
    @JdbcColumn(
            name = "api_model",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "提供商侧模型标识"
    )
    @TableField("api_model")
    private String apiModel;
    /**
     * 启用状态：true 启用，false 禁用。
     */
    @JdbcColumn(
            name = "enabled",
            dataType = "BOOLEAN",
            nullable = false,
            defaultValue = "TRUE",
            comment = "启用状态：true启用，false禁用"
    )
    @TableField("enabled")
    private Boolean enabled;
    /**
     * API Key，当前直接明文存储。
     */
    @JdbcColumn(
            name = "api_key",
            dataType = "VARCHAR(2048)",
            length = 2048,
            nullable = true,
            comment = "API Key，当前直接明文存储"
    )
    @TableField("api_key")
    private String apiKey;
    /**
     * 扩展配置，例如 token 限制、温度参数等。
     */
    @JdbcColumn(
            name = "ext_json",
            dataType = "JSON",
            nullable = true,
            comment = "扩展配置JSON，例如 token 限额、温度参数等"
    )
    @TableField(value = "ext_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extJson;
}
