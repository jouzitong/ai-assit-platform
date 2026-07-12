package ai.platform.aiassit.knowledge.manage.vo;

import ai.platform.aiassit.service.ai.api.enums.AiKbAuthType;
import lombok.Data;

/** 面向管理页面的知识库认证摘要，不包含任何原始密钥。 */
@Data
public class AiKbAuthVO {

    private AiKbAuthType type;

    private String apiKeyMasked;

    private String accessKeyIdMasked;

    private String accessKeySecretMasked;
}
