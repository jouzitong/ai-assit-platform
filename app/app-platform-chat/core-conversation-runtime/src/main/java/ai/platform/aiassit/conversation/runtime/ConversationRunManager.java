package ai.platform.aiassit.conversation.runtime;

import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscriber;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscription;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;

import java.util.Optional;

public interface ConversationRunManager {

    ConversationRunSnapshot start(ConversationQueryCommand command);

    ConversationRunSubscription subscribe(String runId,
                                          Long userId,
                                          String lastEventId,
                                          ConversationRunSubscriber subscriber);

    Optional<ConversationRunSnapshot> find(String runId,
                                           String sessionCode,
                                           String roundCode,
                                           Long userId);

    boolean cancel(String runId, String sessionCode, String roundCode, Long userId);
}
