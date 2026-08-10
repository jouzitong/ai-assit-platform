package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.data.entity.ConversationMemoryBindingEntity;
import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryProviderType;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryScope;
import ai.platform.aiassit.service.ai.spi.memory.MemoryService;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryMessage;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryPageResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationMemoryProviderAccessTest {

    @Test
    void acceptsProviderInternalUserIdAndFiltersAnotherSession() {
        Fixture fixture = fixture();
        MemoryMessage currentSession = message("当前会话记忆", "message-current", "session-1",
                "ragflow-internal-user");
        MemoryMessage anotherSession = message("其他会话记忆", "message-other", "session-2",
                "ragflow-internal-user");
        when(fixture.provider.listMessages(any())).thenReturn(page(currentSession, anotherSession));

        List<MemoryMessage> result = fixture.access.listOwned(
                fixture.binding, "tenant-a", 42L, MemoryScope.SESSION, "session-1", 1, 100);

        assertThat(result).extracting(MemoryMessage::getContent).containsExactly("当前会话记忆");
        assertThat(result.get(0).getMemoryId()).isEqualTo("session-memory-1");
    }

    @Test
    void rejectsResultFromAnotherBoundMemory() {
        Fixture fixture = fixture();
        MemoryMessage foreign = message("其他Memory的记忆", "message-foreign", "session-1",
                "ragflow-internal-user");
        foreign.setMemoryId("memory-owned-by-someone-else");
        when(fixture.provider.listMessages(any())).thenReturn(page(foreign));

        assertThatThrownBy(() -> fixture.access.listOwned(
                fixture.binding, "tenant-a", 42L, MemoryScope.SESSION, "session-1", 1, 100))
                .isInstanceOf(ConversationMemoryProviderAccess.MemoryOwnershipViolationException.class);
    }

    private Fixture fixture() {
        ConversationMemoryProperties properties = new ConversationMemoryProperties();
        properties.setEnabled(true);
        properties.setProviderType(MemoryProviderType.RAGFLOW);

        ConversationMemoryProvisionService provision = mock(ConversationMemoryProvisionService.class);
        ConversationMemoryProviderRegistry registry = mock(ConversationMemoryProviderRegistry.class);
        ConversationMemoryIdentity identity = mock(ConversationMemoryIdentity.class);
        MemoryService provider = mock(MemoryService.class);
        ConversationMemoryBindingEntity binding = new ConversationMemoryBindingEntity();
        binding.setTenantId("tenant-a");
        binding.setUserId(42L);
        binding.setStatus("ACTIVE");
        binding.setSessionMemoryId("session-memory-1");
        binding.setLongTermMemoryId("long-memory-1");
        when(provision.providerMeta("tenant-a")).thenReturn(new RequestMeta());
        when(registry.require(MemoryProviderType.RAGFLOW)).thenReturn(provider);
        when(identity.providerUserId("tenant-a", 42L)).thenReturn("platform-user-owner");
        return new Fixture(new ConversationMemoryProviderAccess(
                provision, registry, identity, properties), binding, provider);
    }

    private MemoryPageResponse page(MemoryMessage... items) {
        MemoryPageResponse response = new MemoryPageResponse();
        response.setItems(List.of(items));
        return response;
    }

    private MemoryMessage message(String content, String messageId, String sessionId, String userId) {
        MemoryMessage message = new MemoryMessage();
        message.setMessageId(messageId);
        message.setContent(content);
        message.setSessionId(sessionId);
        message.setUserId(userId);
        return message;
    }

    private record Fixture(ConversationMemoryProviderAccess access,
                           ConversationMemoryBindingEntity binding,
                           MemoryService provider) {
    }
}
