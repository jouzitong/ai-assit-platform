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

    /**
     * 模型编码。（内部）
     */
    @TableField("model_code")
    private String modelCode;
    /**
     * 模型名称。
     */
    @TableField("model_name")
    private String modelName;
    /**
     * 对话客户端类型。
     *
     * <p>运行时据此选择客户端 Driver；不表示模型供应商。</p>
     */
    @TableField(value = "client_type", typeHandler = DefaultEnumTypeHandler.class)
    private AiChatClientType clientType;
    /**
     * 提供商请求基础地址。
     */
    @TableField("base_url")
    private String baseUrl;
    /**
     * 提供商侧模型标识。
     */
    @TableField("api_model")
    private String apiModel;
    /**
     * 启用状态：true 启用，false 禁用。
     */
    @TableField("enabled")
    private Boolean enabled;
    /**
     * API Key，当前直接明文存储。
     */
    @TableField("api_key")
    private String apiKey;
    /**
     * 扩展配置，例如 token 限制、温度参数等。
     */
    @TableField(value = "ext_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extJson;
}
