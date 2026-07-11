package ai.platform.aiassit.conversation.runtime.cluster;

import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscriber;
import ai.platform.aiassit.conversation.runtime.event.ConversationRunSubscription;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;

import java.util.Optional;
import java.util.function.Consumer;

public class LocalConversationRunClusterCoordinator implements ConversationRunClusterCoordinator {

    private final String nodeId;

    public LocalConversationRunClusterCoordinator(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public String nodeId() {
        return nodeId;
    }

    @Override
    public boolean distributed() {
        return false;
    }

    @Override
    public void registerLocalCancelHandler(Consumer<String> cancelHandler) {
    }

    @Override
    public void save(ConversationRunSnapshot snapshot) {
    }

    @Override
    public void publish(ConversationRunSnapshot snapshot, ConversationQueryStreamEvent event) {
    }

    @Override
    public Optional<ConversationRunSnapshot> find(String runId,
                                                  String sessionCode,
                                                  String roundCode,
                                                  Long userId) {
        return Optional.empty();
    }

    @Override
    public ConversationRunSubscription subscribe(String runId,
                                                 Long userId,
                                                 String lastEventId,
                                                 ConversationRunSubscriber subscriber) {
        throw new IllegalArgumentException("conversation run not found");
    }

    @Override
    public boolean requestCancel(ConversationRunSnapshot snapshot) {
        return false;
    }

    @Override
    public boolean isCancelRequested(String runId) {
        return false;
    }
}
