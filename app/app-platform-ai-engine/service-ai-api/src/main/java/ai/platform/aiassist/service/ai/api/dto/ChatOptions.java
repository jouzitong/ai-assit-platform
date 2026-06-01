package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;



@Data
public class ChatOptions implements Serializable {

    /** 采样温度，越高越发散 */
    private Double temperature;
    /** nucleus sampling 参数 */
    private Double topP;
    /** 最大输出 token 数 */
    private Integer maxTokens;
    /** 请求超时时间（毫秒） */
    private Integer timeoutMs;
}
