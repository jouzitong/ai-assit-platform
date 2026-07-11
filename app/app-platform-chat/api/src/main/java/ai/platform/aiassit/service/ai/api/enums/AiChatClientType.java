package ai.platform.aiassit.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * AI 对话客户端类型。
 *
 * <p>用于在运行时选择创建和调用对话客户端的 Driver，不表示模型供应商。
 * 同一类型可连接多个供应商或私有部署实例；具体连接地址、凭据和远端模型标识
 * 均由模型配置实体提供。</p>
 */
@Getter
public enum AiChatClientType implements IEnum {

    /** 基于 Spring AI 的通用兼容协议客户端。 */
    SPRING_AI(1, "通用 Spring AI 客户端"),
    /** AI Agent 专用客户端。 */
    AI_AGENT(2, "AI Agent 客户端");

    @JsonValue
    private final int code;

    private final String name;

    AiChatClientType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
