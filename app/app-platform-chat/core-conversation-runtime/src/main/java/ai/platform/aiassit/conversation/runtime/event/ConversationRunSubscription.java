package ai.platform.aiassit.conversation.runtime.event;

@FunctionalInterface
public interface ConversationRunSubscription extends AutoCloseable {

    @Override
    void close();
}
