package ai.platform.aiassit.chat.workflow.data.entity.config;

import lombok.Data;

/**
 * AI 节点输入消息配置。
 */
@Data
public class AiNodeMessageConfig {

    private String role;

    private String content;
}
