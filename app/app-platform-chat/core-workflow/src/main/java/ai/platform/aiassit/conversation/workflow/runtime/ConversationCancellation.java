package ai.platform.aiassit.conversation.workflow.runtime;

/**
 * 对话执行协作式取消信号。
 */
@FunctionalInterface
public interface ConversationCancellation {

    ConversationCancellation NONE = () -> false;

    boolean isCancellationRequested();

    default void throwIfCancellationRequested() {
        if (isCancellationRequested() || Thread.currentThread().isInterrupted()) {
            throw new ConversationCancelledException();
        }
    }
}
