package ai.platform.aiassit.chat.meta.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.BaseEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ai_provider_config")
/**
 * AI 提供商配置实体。
 */
public class AiProviderConfigEntity extends BaseEntity {

    /**
     * 提供商编码。
     */
    @TableField("provider_code")
    private String providerCode;
    /**
     * 提供商名称。
     */
    @TableField("provider_name")
    private String providerName;
    /**
     * 提供商请求基础地址。
     */
    @TableField("base_url")
    private String baseUrl;
    /**
     * 连接超时时间（毫秒）。
     */
    @TableField("connect_timeout_ms")
    private Integer connectTimeoutMs;
    /**
     * 读取超时时间（毫秒）。
     */
    @TableField("read_timeout_ms")
    private Integer readTimeoutMs;
    /**
     * 启用状态：true 启用，false 禁用。
     */
    @TableField("enabled")
    private Boolean enabled;
    /**
     * 备注。
     */
    @TableField("remark")
    private String remark;
}
