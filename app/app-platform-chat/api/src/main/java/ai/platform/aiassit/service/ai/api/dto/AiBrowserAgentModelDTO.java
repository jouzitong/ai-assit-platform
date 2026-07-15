package ai.platform.aiassit.service.ai.api.dto;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import lombok.Data;

/**
 * 浏览器 Agent 运行时模型配置。
 *
 * <p>该契约包含原始 API Key，仅供受信任的内网页面直接连接模型使用。</p>
 */
@Data
public class AiBrowserAgentModelDTO {

    private Long id;

    private String modelCode;

    private String modelName;

    private String apiModel;

    private AiChatClientType clientType;

    private String baseUrl;

    private String apiKey;
}
