package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import ai.platform.aiassit.conversation.dto.conversation.ConversationQueryRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultConversationServiceImplTest {

    @Test
    void defaultListExcludesPageAssistantButExplicitQueryCanLoadIt() {
        AiChatSessionDTO custom = session("custom", AiChatBusinessType.CUSTOM);
        AiChatSessionDTO general = session("general", AiChatBusinessType.GENERAL);
        AiChatSessionDTO assistant = session("assistant", AiChatBusinessType.PAGE_ASSISTANT);
        DefaultConversationServiceImpl service = new DefaultConversationServiceImpl(
                sessionService(List.of(custom, general, assistant)), null, null, null, null);

        assertThat(service.listConversations(new ConversationQueryRequest()))
                .extracting(AiChatSessionDTO::getSessionCode)
                .containsExactly("custom", "general");

        ConversationQueryRequest assistantQuery = new ConversationQueryRequest();
        assistantQuery.setBusinessType(AiChatBusinessType.PAGE_ASSISTANT);
        assertThat(service.listConversations(assistantQuery))
                .extracting(AiChatSessionDTO::getSessionCode)
                .containsExactly("assistant");
    }

    private AiChatSessionDTO session(String code, AiChatBusinessType businessType) {
        AiChatSessionDTO session = new AiChatSessionDTO();
        session.setSessionCode(code);
        session.setBusinessType(businessType);
        return session;
    }

    private AiChatSessionService sessionService(List<AiChatSessionDTO> sessions) {
        return (AiChatSessionService) Proxy.newProxyInstance(
                AiChatSessionService.class.getClassLoader(),
                new Class<?>[]{AiChatSessionService.class},
                (proxy, method, args) -> {
                    if ("queryAll".equals(method.getName())) {
                        AiChatHistoryQueryRequest query = (AiChatHistoryQueryRequest) args[0];
                        if (query.getBusinessType() == null) {
                            return sessions;
                        }
                        return sessions.stream()
                                .filter(session -> session.getBusinessType() == query.getBusinessType())
                                .toList();
                    }
                    if ("toString".equals(method.getName())) {
                        return "AiChatSessionServiceStub";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    return null;
                });
    }
}
