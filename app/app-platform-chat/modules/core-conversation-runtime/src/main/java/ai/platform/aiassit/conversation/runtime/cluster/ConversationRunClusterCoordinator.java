package ai.platform.aiassit.conversation.runtime.cluster;

import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscriber;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscription;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;

import java.util.Optional;
import java.util.function.Consumer;

public interface ConversationRunClusterCoordinator {

    String nodeId();

    boolean distributed();

    void registerLocalCancelHandler(Consumer<String> cancelHandler);

    void save(ConversationRunSnapshot snapshot);

    void publish(ConversationRunSnapshot snapshot, ConversationQueryStreamEvent event);

    Optional<ConversationRunSnapshot> find(String runId,
                                           String sessionCode,
                                           String roundCode,
                                           Long userId);

    ConversationRunSubscription subscribe(String runId,
                                          Long userId,
                                          String lastEventId,
                                          ConversationRunSubscriber subscriber);

    boolean requestCancel(ConversationRunSnapshot snapshot);

    boolean isCancelRequested(String runId);
}
