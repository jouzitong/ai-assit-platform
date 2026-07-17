package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.data.entity.req.ConversationHistoryQueryRequest;
import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
import ai.platform.aiassit.conversation.data.service.ConversationSessionService;
import ai.platform.aiassit.conversation.dto.conversation.ConversationQueryRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultConversationServiceImplTest {

    @Test
    void defaultListExcludesPageAssistantButExplicitQueryCanLoadIt() {
        ConversationSessionDTO custom = session("custom", ConversationBusinessType.CUSTOM);
        ConversationSessionDTO general = session("general", ConversationBusinessType.GENERAL);
        ConversationSessionDTO assistant = session("assistant", ConversationBusinessType.PAGE_ASSISTANT);
        DefaultConversationServiceImpl service = new DefaultConversationServiceImpl(
                sessionService(List.of(custom, general, assistant)), null, null, null, null);

        assertThat(service.listConversations(new ConversationQueryRequest()))
                .extracting(ConversationSessionDTO::getSessionCode)
                .containsExactly("custom", "general");

        ConversationQueryRequest assistantQuery = new ConversationQueryRequest();
        assistantQuery.setBusinessType(ConversationBusinessType.PAGE_ASSISTANT);
        assertThat(service.listConversations(assistantQuery))
                .extracting(ConversationSessionDTO::getSessionCode)
                .containsExactly("assistant");
    }

    private ConversationSessionDTO session(String code, ConversationBusinessType businessType) {
        ConversationSessionDTO session = new ConversationSessionDTO();
        session.setSessionCode(code);
        session.setBusinessType(businessType);
        return session;
    }

    private ConversationSessionService sessionService(List<ConversationSessionDTO> sessions) {
        return (ConversationSessionService) Proxy.newProxyInstance(
                ConversationSessionService.class.getClassLoader(),
                new Class<?>[]{ConversationSessionService.class},
                (proxy, method, args) -> {
                    if ("queryAll".equals(method.getName())) {
                        ConversationHistoryQueryRequest query = (ConversationHistoryQueryRequest) args[0];
                        if (query.getBusinessType() == null) {
                            return sessions;
                        }
                        return sessions.stream()
                                .filter(session -> session.getBusinessType() == query.getBusinessType())
                                .toList();
                    }
                    if ("toString".equals(method.getName())) {
                        return "ConversationSessionServiceStub";
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
