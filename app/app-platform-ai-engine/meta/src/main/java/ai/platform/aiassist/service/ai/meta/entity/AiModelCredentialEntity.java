package ai.platform.aiassist.service.ai.meta.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.BaseEntity;
import java.time.LocalDateTime;

/**
 * AI 模型密钥配置实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_credential")
public class AiModelCredentialEntity extends BaseEntity {

    /** 密钥配置编码。 */
    @TableField("credential_code")
    private String credentialCode;
    /** 提供商编码。 */
    @TableField("provider_code")
    private String providerCode;
    /** 模型编码。 */
    @TableField("model_code")
    private String modelCode;
    /** API Key 密文。 */
    @TableField("api_key_ciphertext")
    private String apiKeyCiphertext;
    /** API Key 脱敏展示值。 */
    @TableField("api_key_masked")
    private String apiKeyMasked;
    /** 密钥版本号。 */
    @TableField("key_version")
    private Integer keyVersion;
    /** 启用状态：true 启用，false 禁用。 */
    @TableField("enabled")
    private Boolean enabled;
    /** 密钥过期时间。 */
    @TableField("expire_at")
    private LocalDateTime expireAt;
    /** 备注。 */
    @TableField("remark")
    private String remark;
}
