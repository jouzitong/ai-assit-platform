package ai.platform.aiassit.conversation.constant;

/**
 * 会话流式事件类型。
 *
 * <p>eventType 只表达前端真正关心的会话功能事件，
 * 不承载节点、流程或能力阶段信息。</p>
 *
 * @author zhouzhitong
 * @since 2026/7/6
 */
public final class ConversationEventTypes {

    private ConversationEventTypes() {
    }

    /**
     * AI 执行进度事件。
     */
    public static final String PROGRESS = "progress";

    /**
     * AI 回答增量事件，用于流式追加回答内容。
     */
    public static final String ANSWER_DELTA = "answer_delta";

    /**
     * AI 最终回答事件。
     */
    public static final String ANSWER = "answer";

    /**
     * 澄清事件，用于提示用户补充或确认信息。
     */
    public static final String CLARIFICATION = "clarification";

    /**
     * 错误事件。
     */
    public static final String ERROR = "error";

    /**
     * 会话流程全局完成事件。
     */
    public static final String COMPLETE = "complete";
}
