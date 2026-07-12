package ai.platform.aiassit.service.ai.api.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKbAuthType;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识库 Provider 的连接认证信息。
 *
 * <p>认证对象随知识库配置持久化；{@code workspaceId} 等 Provider 业务参数仍放在
 * {@code extJson}，避免认证模型被 Provider 专属参数污染。</p>
 */
@Data
public class AiKbAuthConfig implements Serializable {

    private AiKbAuthType type;

    /** Bearer 认证使用的 API Key。 */
    private String apiKey;

    /** 阿里云 AK/SK 认证使用的 AccessKey ID。 */
    private String accessKeyId;

    /** 阿里云 AK/SK 认证使用的 AccessKey Secret。 */
    private String accessKeySecret;
}
