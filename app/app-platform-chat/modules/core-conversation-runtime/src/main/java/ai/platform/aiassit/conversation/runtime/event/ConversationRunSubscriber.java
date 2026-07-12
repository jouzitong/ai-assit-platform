package ai.platform.aiassit.conversation.runtime.event;

import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;

@FunctionalInterface
public interface ConversationRunSubscriber {

    void onEvent(ConversationQueryStreamEvent event) throws Exception;

    default void onComplete() { }
}
