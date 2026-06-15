package ai.platform.aiassist.service.ai.meta.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.BaseEntity;

/**
 * AI 模型配置实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_config")
public class AiModelConfigEntity extends BaseEntity {

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
     * 所属提供商编码。
     */
    @TableField("provider_code")
    private String providerCode;
    /**
     * 提供商侧模型标识。
     */
    @TableField("api_model")
    private String apiModel;
    /**
     * 能力标签，多个标签用逗号分隔。
     */
    @TableField("capability_tags")
    private String capabilityTags;
    /**
     * 最大上下文 token 数。
     */
    @TableField("max_context_tokens")
    private Integer maxContextTokens;
    /**
     * 最大输出 token 数。
     */
    @TableField("max_output_tokens")
    private Integer maxOutputTokens;
    /**
     * 是否启用温度参数：1 启用，0 禁用。
     */
    @TableField("temperature_enabled")
    private Integer temperatureEnabled;
    /**
     * 启用状态：true 启用，false 禁用。
     */
    @TableField("enabled")
    private Boolean enabled;
    /**
     * 优先级，越小优先级越高。
     */
    @TableField("priority")
    private Integer priority;
    /**
     * 备注。
     */
    @TableField("remark")
    private String remark;
}
