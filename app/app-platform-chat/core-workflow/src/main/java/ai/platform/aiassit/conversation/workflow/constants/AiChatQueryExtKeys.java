package ai.platform.aiassit.conversation.workflow.constants;

/**
 * AI 对话查询扩展参数 key 常量。
 *
 * @author zhouzhitong
 * @since 2026/6/23
 */
public interface AiChatQueryExtKeys {

    /**
     * 是否允许更新会话标题。
     *
     * <p>默认仅首轮消息允许更新标题；当该值显式为 true 时，允许后续轮次继续更新。</p>
     */
    String ALLOW_UPDATE_SESSION_NAME = "allowUpdateSessionName";
}
