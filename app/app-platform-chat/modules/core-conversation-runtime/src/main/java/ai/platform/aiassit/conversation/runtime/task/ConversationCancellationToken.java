package ai.platform.aiassit.conversation.runtime.task;

import ai.platform.aiassit.conversation.workflow.runtime.ConversationCancellation;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public final class ConversationCancellationToken implements ConversationCancellation {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private final BooleanSupplier externalCancellation;

    public ConversationCancellationToken() {
        this(() -> false);
    }

    public ConversationCancellationToken(BooleanSupplier externalCancellation) {
        this.externalCancellation = externalCancellation == null ? () -> false : externalCancellation;
    }

    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    @Override
    public boolean isCancellationRequested() {
        return cancelled.get() || externalCancellation.getAsBoolean();
    }
}
