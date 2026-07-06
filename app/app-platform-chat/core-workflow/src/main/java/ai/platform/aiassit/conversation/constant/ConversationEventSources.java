package ai.platform.aiassit.conversation.constant;

/**
 * 会话流式事件来源。
 *
 * @author zhouzhitong
 * @since 2026/7/6
 */
public final class ConversationEventSources {

    private ConversationEventSources() {
    }

    /**
     * 会话级事件来源。
     */
    public static final String CONVERSATION = "CONVERSATION";

    /**
     * 工作流执行事件来源。
     */
    public static final String WORKFLOW = "WORKFLOW";

    /**
     * 聊天消息事件来源。
     */
    public static final String CHAT_MESSAGE = "CHAT_MESSAGE";

    /**
     * 意图分析事件来源。
     */
    public static final String INTENT_ANALYZE = "INTENT_ANALYZE";

    /**
     * 查询计划事件来源。
     */
    public static final String QUERY_PLAN = "QUERY_PLAN";

    /**
     * 简单聊天事件来源。
     */
    public static final String SIMPLE_CHAT = "SIMPLE_CHAT";

    /**
     * 页面渲染事件来源。
     */
    public static final String RENDER = "RENDER";

    /**
     * 结果评估事件来源。
     */
    public static final String EVALUATION = "EVALUATION";
}
