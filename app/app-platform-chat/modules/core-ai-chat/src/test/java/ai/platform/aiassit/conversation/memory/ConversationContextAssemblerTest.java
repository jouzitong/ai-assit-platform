package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.data.entity.ConversationMemoryBindingEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryProviderType;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import ai.platform.aiassit.service.ai.spi.memory.MemoryService;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryMessage;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemorySearchResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemorySearchRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationContextAssemblerTest {

    @Test
    void filtersDisabledAndDuplicateMemoryAndKeepsShadowResultOutOfAgentInput() {
        Fixture fixture = fixture();
        MemoryMessage duplicate = message("最近原文", "session-message", "session-1", "provider-user", MemoryType.SEMANTIC);
        MemoryMessage session = message("早期会话事实", "session-memory", "session-1", "provider-user", MemoryType.EPISODIC);
        session.setSimilarity(0.9D);
        MemoryMessage longTerm = message("长期偏好", "long-memory", "old-session", "provider-user", MemoryType.PROCEDURAL);
        longTerm.setSimilarity(0.8D);
        MemoryMessage disabled = message("已停用", "disabled-memory", "session-1", "provider-user", MemoryType.SEMANTIC);
        disabled.setEnabled(false);
        when(fixture.provider.searchMessages(any())).thenAnswer(invocation -> {
            ProviderMemorySearchRequest request = invocation.getArgument(0);
            return response("session-1".equals(request.getSessionId())
                    ? List.of(duplicate, session, disabled) : List.of(longTerm));
        });
        fixture.context.getOrCreateUserMessageContext().setSessionMessages(List.of(userMessage("最近原文")));

        fixture.assembler.assemble(fixture.context);

        assertThat(fixture.context.getContextPackage().getSessionMemories())
                .extracting(item -> item.getContent())
                .containsExactly("早期会话事实");
        assertThat(fixture.context.getContextPackage().getLongTermMemories())
                .extracting(item -> item.getContent())
                .containsExactly("长期偏好");
        assertThat(fixture.context.getContextPackage().isInjectionEnabled()).isFalse();
        assertThat(fixture.context.getContextPackage().getDegradedReason()).isNull();
    }

    @Test
    void rejectsAllCandidatesWhenProviderReturnsAnotherUsersMessage() {
        Fixture fixture = fixture();
        MemoryMessage foreign = message("别的用户私密事实", "foreign", "session-1", "provider-user-other", MemoryType.SEMANTIC);
        when(fixture.provider.searchMessages(any())).thenReturn(response(List.of(foreign)));

        fixture.assembler.assemble(fixture.context);

        assertThat(fixture.context.getContextPackage().getSessionMemories()).isEmpty();
        assertThat(fixture.context.getContextPackage().getLongTermMemories()).isEmpty();
        assertThat(fixture.context.getContextPackage().getDegradedReason())
                .isEqualTo("MEMORY_OWNERSHIP_MISMATCH");
    }

    private Fixture fixture() {
        ConversationMemoryProperties properties = new ConversationMemoryProperties();
        properties.setEnabled(true);
        properties.setIdentitySalt("test-memory-salt");
        properties.setProviderType(MemoryProviderType.RAGFLOW);
        properties.getRecall().setInjectionEnabled(false);
        properties.getRecall().setTokenBudget(200);

        ConversationMemoryProvisionService provision = mock(ConversationMemoryProvisionService.class);
        ConversationMemoryProviderRegistry registry = mock(ConversationMemoryProviderRegistry.class);
        ConversationMemoryIdentity identity = mock(ConversationMemoryIdentity.class);
        ai.platform.aiassit.conversation.data.service.ConversationMemorySyncTaskDataService tasks =
                mock(ai.platform.aiassit.conversation.data.service.ConversationMemorySyncTaskDataService.class);
        MemorySessionPolicyService policy = mock(MemorySessionPolicyService.class);
        MemoryService provider = mock(MemoryService.class);
        ConversationMemoryBindingEntity binding = new ConversationMemoryBindingEntity();
        binding.setTenantId("tenant-a");
        binding.setUserId(42L);
        binding.setStatus("ACTIVE");
        binding.setSchemaVersion(1);
        binding.setSessionMemoryId("session-memory-1");
        binding.setLongTermMemoryId("long-memory-1");
        when(provision.findActive("tenant-a", 42L)).thenReturn(binding);
        when(provision.providerMeta("tenant-a")).thenReturn(new RequestMeta());
        when(registry.require(MemoryProviderType.RAGFLOW)).thenReturn(provider);
        when(identity.providerUserId("tenant-a", 42L)).thenReturn("provider-user");
        when(tasks.hasOutstanding("tenant-a", 42L, "session-1")).thenReturn(false);
        when(policy.excludedMessageKeys("tenant-a", 42L, "session-1")).thenReturn(Set.of());
        when(policy.key(any(), any())).thenAnswer(invocation ->
                invocation.getArgument(0, String.class) + "\u001f"
                        + invocation.getArgument(1, String.class));

        ConversationRuntimeContext context = new ConversationRuntimeContext();
        context.setTenantId("tenant-a");
        ConversationSessionDTO session = new ConversationSessionDTO();
        session.setUserId(42L);
        session.setSessionCode("session-1");
        context.setSession(session);
        ConversationQueryCommand command = new ConversationQueryCommand();
        command.setMessage("当前问题");
        context.setCommand(command);
        return new Fixture(new ConversationContextAssembler(
                provision, registry, identity, tasks, policy, new ContextBudgetPlanner(properties),
                properties, directExecutor()), context, provider);
    }

    private Executor directExecutor() {
        return Runnable::run;
    }

    private MemorySearchResponse response(List<MemoryMessage> items) {
        MemorySearchResponse response = new MemorySearchResponse();
        response.setItems(items);
        return response;
    }

    private MemoryMessage message(String content,
                                  String messageId,
                                  String sessionId,
                                  String userId,
                                  MemoryType type) {
        MemoryMessage message = new MemoryMessage();
        // Search is scoped to one binding ID per request; older deployments may omit memory_id.
        message.setMemoryId(null);
        message.setMessageId(messageId);
        message.setContent(content);
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setMemoryType(type);
        message.setEnabled(true);
        return message;
    }

    private ConversationMessageDTO userMessage(String content) {
        ConversationMessageDTO message = new ConversationMessageDTO();
        message.setRole("USER");
        message.setContent(content);
        return message;
    }

    private record Fixture(ConversationContextAssembler assembler,
                           ConversationRuntimeContext context,
                           MemoryService provider) {
    }
}
