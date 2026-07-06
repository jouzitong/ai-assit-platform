package ai.platform.aiassit.conversation.constant;

/**
 * 会话流式事件阶段。
 *
 * @author zhouzhitong
 * @since 2026/7/6
 */
public final class ConversationEventPhases {

    private ConversationEventPhases() {
    }

    /**
     * 已开始阶段。
     */
    public static final String STARTED = "STARTED";

    /**
     * 执行中阶段。
     */
    public static final String RUNNING = "RUNNING";

    /**
     * 已就绪阶段。
     */
    public static final String READY = "READY";

    /**
     * 已完成阶段。
     */
    public static final String COMPLETED = "COMPLETED";

    /**
     * 已跳过阶段。
     */
    public static final String SKIPPED = "SKIPPED";

    /**
     * 执行失败阶段。
     */
    public static final String FAILED = "FAILED";
}
