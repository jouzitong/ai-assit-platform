package ai.platform.aiassit.db.engine.meta.entity.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源认证配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbDataSourceAuthConfig {

    /** 认证类型，例如 NONE、BASIC、BEARER、AK_SK、API_KEY。 */
    private String authType;

    /** 用户名。 */
    private String username;

    /** 密码密文或引用。 */
    private String passwordCiphertext;

    /** Token 密文或引用。 */
    private String tokenCiphertext;

    /** Access Key。 */
    private String accessKey;

    /** Secret Key 密文或引用。 */
    private String secretKeyCiphertext;

    /** 凭证引用标识。 */
    private String credentialRef;
}
