package ai.platform.aiassit.conversation.workflow.runtime;

import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;

/**
 * 对话执行事件发布端口。
 *
 * <p>工作流只负责产生事件，不感知事件最终通过 SSE、WebSocket 或其他传输方式发送。</p>
 */
@FunctionalInterface
public interface ConversationEventPublisher {

    ConversationEventPublisher NOOP = event -> { };

    void publish(ConversationQueryStreamEvent event);
}
