package ai.platform.aiassit.execution.service;

import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import lombok.Data;

/**
 * 可供管理界面选择的知识库客户端配置摘要。
 *
 * <p>该对象仅返回脱敏后的认证值；机密信息仅在服务端解析并注入到 Provider 调用上下文。</p>
 */
@Data
public class KnowledgeClientOption {

    /** 系统配置中客户端的稳定标识。 */
    private String key;

    /** 对应的知识库 Provider 类型。 */
    private AiKnowledgeClientType clientType;

    /** Provider 服务访问地址。 */
    private String url;

    /** 仅用于页面说明的认证类型，例如 bearer、header、none。 */
    private String authType;

    /** 脱敏后的 API Key 或 Token，仅展示前 8 位和后 4 位。 */
    private String authValueMasked;

    /** 阿里云 AK/SK 场景中展示的脱敏 AccessKey ID。 */
    private String accessKeyIdMasked;
}
