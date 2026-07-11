package ai.platform.aiassit.conversation.workflow.runtime;

/**
 * 对话任务被用户主动取消。
 */
public class ConversationCancelledException extends RuntimeException {

    public ConversationCancelledException() {
        super("conversation run cancelled");
    }
}
