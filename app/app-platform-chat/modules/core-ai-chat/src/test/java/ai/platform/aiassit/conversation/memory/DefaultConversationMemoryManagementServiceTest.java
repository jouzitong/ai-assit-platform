package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.data.entity.ConversationMemoryBindingEntity;
import ai.platform.aiassit.conversation.data.service.ConversationMemorySyncTaskDataService;
import ai.platform.aiassit.conversation.data.service.ConversationSessionService;
import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryCreateRequest;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryItemStatus;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryWriteResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryWriteRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultConversationMemoryManagementServiceTest {

    @Test
    void createsConfirmedLongTermMemoryOnlyInTheProvider() {
        ConversationMemoryProvisionService provisionService = mock(ConversationMemoryProvisionService.class);
        ConversationMemoryProviderAccess providerAccess = mock(ConversationMemoryProviderAccess.class);
        ConversationMemoryBindingEntity binding = activeBinding();
        when(provisionService.findActive("tenant-a", 42L)).thenReturn(binding);
        when(providerAccess.providerUserId("tenant-a", 42L)).thenReturn("provider-user-a");
        when(providerAccess.addConversation(eq("tenant-a"), any(ProviderMemoryWriteRequest.class)))
                .thenReturn(acceptedWrite());

        ConversationMemoryCreateRequest request = new ConversationMemoryCreateRequest();
        request.setContent("用户偏好以列表方式展示数据");
        request.setConfirmed(true);

        var response = service(provisionService, providerAccess).createLongTerm("tenant-a", 42L, request);

        var write = org.mockito.ArgumentCaptor.forClass(ProviderMemoryWriteRequest.class);
        verify(providerAccess).addConversation(eq("tenant-a"), write.capture());
        assertThat(write.getValue().getMemoryIds()).containsExactly("long-memory-1");
        assertThat(write.getValue().getUserId()).isEqualTo("provider-user-a");
        assertThat(write.getValue().getSessionId()).isEqualTo("long-term-manual");
        assertThat(write.getValue().getAgentId()).isEqualTo("platform-memory-manager");
        assertThat(write.getValue().getUserInput()).isEqualTo("用户偏好以列表方式展示数据");
        assertThat(response.isAccepted()).isTrue();
        assertThat(response.getStatus()).isEqualTo(MemoryItemStatus.PROCESSING);
        assertThat(response.getMemoryRef()).isNull();
    }

    private DefaultConversationMemoryManagementService service(
            ConversationMemoryProvisionService provisionService,
            ConversationMemoryProviderAccess providerAccess) {
        ConversationMemoryProperties properties = new ConversationMemoryProperties();
        properties.setEnabled(true);
        properties.setLongTermEnabled(true);
        properties.setIdentitySalt("test-salt");
        return new DefaultConversationMemoryManagementService(
                provisionService,
                providerAccess,
                mock(ConversationMemorySyncTaskDataService.class),
                mock(ConversationSessionService.class),
                mock(MemorySessionPolicyService.class),
                mock(ConversationMemoryBridge.class),
                new MemoryReferenceCodec(properties),
                properties);
    }

    private ConversationMemoryBindingEntity activeBinding() {
        ConversationMemoryBindingEntity binding = new ConversationMemoryBindingEntity();
        binding.setTenantId("tenant-a");
        binding.setUserId(42L);
        binding.setStatus("ACTIVE");
        binding.setLongTermMemoryId("long-memory-1");
        return binding;
    }

    private MemoryWriteResponse acceptedWrite() {
        MemoryWriteResponse response = new MemoryWriteResponse();
        response.setAccepted(true);
        response.setProviderMessageId("provider-message-1");
        return response;
    }
}
