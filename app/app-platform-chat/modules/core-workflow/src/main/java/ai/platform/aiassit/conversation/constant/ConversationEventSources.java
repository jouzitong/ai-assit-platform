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

    /** Server-side Agent runtime and its normalized collaboration events. */
    public static final String AI_AGENT = "AI_AGENT";

}
