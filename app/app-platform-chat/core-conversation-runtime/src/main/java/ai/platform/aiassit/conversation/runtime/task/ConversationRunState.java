package ai.platform.aiassit.conversation.runtime.task;

public enum ConversationRunState {
    ACCEPTED,
    RUNNING,
    WAITING_INPUT,
    CANCELLING,
    CANCELLED,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == CANCELLED || this == COMPLETED || this == FAILED;
    }

    public boolean isActive() {
        return !isTerminal();
    }
}
