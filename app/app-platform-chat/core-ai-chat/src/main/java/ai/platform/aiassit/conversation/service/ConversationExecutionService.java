package ai.platform.aiassit.conversation.service;

import ai.platform.aiassit.conversation.dto.chat.ConversationQueryResponse;
import ai.platform.aiassit.conversation.dto.chat.ConversationStreamReconnectRequest;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationCancellation;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationEventPublisher;

import java.util.List;

public interface ConversationExecutionService {

    ConversationQueryResponse execute(ConversationQueryCommand command);

    ConversationQueryResponse executeStream(ConversationQueryCommand command,
                                            ConversationEventPublisher eventPublisher,
                                            ConversationCancellation cancellation);

    List<ConversationQueryStreamEvent> replayStream(ConversationStreamReconnectRequest request,
                                                    Long userId,
                                                    String traceId);
}
