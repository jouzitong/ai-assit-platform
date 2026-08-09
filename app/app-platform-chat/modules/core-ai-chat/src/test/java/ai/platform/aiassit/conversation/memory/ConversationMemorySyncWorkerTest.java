package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.data.entity.ConversationMemoryBindingEntity;
import ai.platform.aiassit.conversation.data.entity.ConversationMemorySyncTaskEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import ai.platform.aiassit.conversation.data.service.ConversationMemorySyncTaskDataService;
import ai.platform.aiassit.conversation.data.service.ConversationMessageService;
import ai.platform.aiassit.conversation.data.service.ConversationRoundService;
import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryScope;
import ai.platform.aiassit.service.ai.spi.memory.MemoryProviderException;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryMessage;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryWriteResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationMemorySyncWorkerTest {

    @Test
    void readsSuccessfulRoundTextAtExecutionTimeAndWritesOnlyToProvider() {
        ConversationMemorySyncTaskDataService tasks = mock(ConversationMemorySyncTaskDataService.class);
        ConversationRoundService rounds = mock(ConversationRoundService.class);
        ConversationMessageService messages = mock(ConversationMessageService.class);
        ConversationMemoryProvisionService provision = mock(ConversationMemoryProvisionService.class);
        ConversationMemoryProviderAccess access = mock(ConversationMemoryProviderAccess.class);
        ConversationMemoryBindingEntity binding = binding();
        when(provision.ensureBinding("tenant-a", 42L)).thenReturn(binding);
        ConversationRoundDTO round = round("SUCCESS");
        round.setRootAgentCode("trusted-agent");
        when(rounds.queryOwned("round-1", "session-1", 42L)).thenReturn(round);
        when(messages.queryByRoundCode("round-1")).thenReturn(List.of(
                message("USER", "用户问题"), message("ASSISTANT", "助手回答")));
        when(access.addConversation(eq("tenant-a"), any())).thenReturn(accepted("provider-message-1"));
        ConversationMemorySyncTaskEntity task = task("ADD_ROUND", "SESSION");

        worker(tasks, rounds, messages, provision, access).process(task);

        var request = org.mockito.ArgumentCaptor.forClass(
                ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryWriteRequest.class);
        verify(access).addConversation(eq("tenant-a"), request.capture());
        assertThat(request.getValue().getUserInput()).isEqualTo("用户问题");
        assertThat(request.getValue().getAgentResponse()).isEqualTo("助手回答");
        assertThat(request.getValue().getMemoryIds()).containsExactly("session-memory-1");
        assertThat(request.getValue().getAgentId()).isEqualTo("trusted-agent");
        assertThat(task.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(task.getProviderMessageId()).isEqualTo("provider-message-1");
        verify(tasks).update(task);
    }

    @Test
    void doesNotWriteFailedOrCancelledRound() {
        ConversationMemorySyncTaskDataService tasks = mock(ConversationMemorySyncTaskDataService.class);
        ConversationRoundService rounds = mock(ConversationRoundService.class);
        ConversationMessageService messages = mock(ConversationMessageService.class);
        ConversationMemoryProvisionService provision = mock(ConversationMemoryProvisionService.class);
        ConversationMemoryProviderAccess access = mock(ConversationMemoryProviderAccess.class);
        when(rounds.queryOwned("round-1", "session-1", 42L)).thenReturn(round("FAILED"));
        ConversationMemorySyncTaskEntity task = task("ADD_ROUND", "SESSION");

        worker(tasks, rounds, messages, provision, access).process(task);

        assertThat(task.getStatus()).isEqualTo("DEAD");
        verifyNoInteractions(access);
        verify(tasks).update(task);
    }

    @Test
    void promotesByReadingSourceTextFromRagflowAtWorkerTime() {
        ConversationMemorySyncTaskDataService tasks = mock(ConversationMemorySyncTaskDataService.class);
        ConversationRoundService rounds = mock(ConversationRoundService.class);
        ConversationMessageService messages = mock(ConversationMessageService.class);
        ConversationMemoryProvisionService provision = mock(ConversationMemoryProvisionService.class);
        ConversationMemoryProviderAccess access = mock(ConversationMemoryProviderAccess.class);
        ConversationMemoryBindingEntity binding = binding();
        when(provision.ensureBinding("tenant-a", 42L)).thenReturn(binding);
        MemoryMessage source = new MemoryMessage();
        source.setMemoryId("session-memory-1");
        source.setMessageId("source-message-1");
        source.setUserId("provider-user");
        source.setSessionId("session-1");
        source.setMemoryType(MemoryType.SEMANTIC);
        source.setEnabled(true);
        source.setContent("用户确认的长期偏好");
        when(access.requireOwnedMessage(binding, "tenant-a", 42L,
                ai.platform.aiassit.service.ai.api.memory.enums.MemoryScope.SESSION,
                "session-1", "source-message-1")).thenReturn(source);
        when(access.providerUserId("tenant-a", 42L)).thenReturn("provider-user");
        when(access.addConversation(eq("tenant-a"), any())).thenReturn(accepted("long-message-1"));
        ConversationMemorySyncTaskEntity task = task("PROMOTE", "LONG_TERM");
        task.setSourceMemoryId("session-memory-1");
        task.setSourceMessageId("source-message-1");

        worker(tasks, rounds, messages, provision, access).process(task);

        var request = org.mockito.ArgumentCaptor.forClass(
                ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryWriteRequest.class);
        verify(access).addConversation(eq("tenant-a"), request.capture());
        assertThat(request.getValue().getMemoryIds()).containsExactly("long-memory-1");
        assertThat(request.getValue().getUserInput()).isEqualTo("用户确认的长期偏好");
        assertThat(request.getValue().getAgentId()).isEqualTo("platform-memory-promoter");
        assertThat(task.getStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void deletesOnlyTheRequestedSessionFromTheSharedProviderMemory() {
        ConversationMemorySyncTaskDataService tasks = mock(ConversationMemorySyncTaskDataService.class);
        ConversationRoundService rounds = mock(ConversationRoundService.class);
        ConversationMessageService messages = mock(ConversationMessageService.class);
        ConversationMemoryProvisionService provision = mock(ConversationMemoryProvisionService.class);
        ConversationMemoryProviderAccess access = mock(ConversationMemoryProviderAccess.class);
        ConversationMemoryBindingEntity binding = binding();
        when(provision.findActive("tenant-a", 42L)).thenReturn(binding);
        ConversationMemorySyncTaskEntity task = task("DELETE_SESSION", "SESSION");
        task.setRoundCode(null);
        task.setTargetMemoryId("session-memory-1");

        worker(tasks, rounds, messages, provision, access).process(task);

        verify(access).forgetOwnedSession(binding, "tenant-a", 42L, "session-1", "session-memory-1");
        assertThat(task.getStatus()).isEqualTo("SUCCEEDED");
        verify(tasks).update(task);
        verifyNoInteractions(rounds, messages);
    }

    @Test
    void turnsUncertainProviderWriteIntoUnknownInsteadOfBlindRetry() {
        ConversationMemorySyncTaskDataService tasks = mock(ConversationMemorySyncTaskDataService.class);
        ConversationRoundService rounds = mock(ConversationRoundService.class);
        ConversationMessageService messages = mock(ConversationMessageService.class);
        ConversationMemoryProvisionService provision = mock(ConversationMemoryProvisionService.class);
        ConversationMemoryProviderAccess access = mock(ConversationMemoryProviderAccess.class);
        when(rounds.queryOwned("round-1", "session-1", 42L)).thenReturn(round("SUCCESS"));
        when(messages.queryByRoundCode("round-1")).thenReturn(List.of(
                message("USER", "问题"), message("ASSISTANT", "回答")));
        when(provision.ensureBinding("tenant-a", 42L)).thenReturn(binding());
        when(access.addConversation(eq("tenant-a"), any()))
                .thenThrow(new MemoryProviderException("TIMEOUT", "unknown", true, null));
        ConversationMemorySyncTaskEntity task = task("ADD_ROUND", "SESSION");

        worker(tasks, rounds, messages, provision, access).process(task);

        assertThat(task.getStatus()).isEqualTo("UNKNOWN");
        assertThat(task.getLastErrorCode()).isEqualTo("TIMEOUT");
        verify(tasks).update(task);
    }

    @Test
    void reconcilesUnknownByExternalIdAndDoesNotPostAgainWhenFound() {
        ConversationMemorySyncTaskDataService tasks = mock(ConversationMemorySyncTaskDataService.class);
        ConversationRoundService rounds = mock(ConversationRoundService.class);
        ConversationMessageService messages = mock(ConversationMessageService.class);
        ConversationMemoryProvisionService provision = mock(ConversationMemoryProvisionService.class);
        ConversationMemoryProviderAccess access = mock(ConversationMemoryProviderAccess.class);
        ConversationMemoryBindingEntity binding = binding();
        when(provision.findActive("tenant-a", 42L)).thenReturn(binding);
        MemoryMessage found = new MemoryMessage();
        found.setMessageId("provider-message-42");
        found.setExternalId("task-key");
        when(access.findOwnedByExternalId(binding, "tenant-a", 42L, MemoryScope.SESSION,
                "session-1", "task-key")).thenReturn(found);
        ConversationMemoryProperties properties = properties();
        properties.getSync().setProviderIdempotencyEnabled(true);
        ConversationMemorySyncTaskEntity task = task("ADD_ROUND", "SESSION");
        task.setStatus("UNKNOWN");
        task.setIdempotencyKey("task-key");
        task.setTargetMemoryId("session-memory-1");

        worker(tasks, rounds, messages, provision, access, properties).process(task);

        assertThat(task.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(task.getProviderMessageId()).isEqualTo("provider-message-42");
        verify(access).findOwnedByExternalId(binding, "tenant-a", 42L, MemoryScope.SESSION,
                "session-1", "task-key");
        verify(access, org.mockito.Mockito.never()).addConversation(any(), any());
        verify(tasks).update(task);
    }

    @Test
    void keepsUnknownWithoutBlindRetryWhenProviderIdempotencyIsDisabled() {
        ConversationMemorySyncTaskDataService tasks = mock(ConversationMemorySyncTaskDataService.class);
        ConversationRoundService rounds = mock(ConversationRoundService.class);
        ConversationMessageService messages = mock(ConversationMessageService.class);
        ConversationMemoryProvisionService provision = mock(ConversationMemoryProvisionService.class);
        ConversationMemoryProviderAccess access = mock(ConversationMemoryProviderAccess.class);
        ConversationMemoryProperties properties = properties();
        ConversationMemorySyncTaskEntity task = task("ADD_ROUND", "SESSION");
        task.setStatus("UNKNOWN");
        task.setIdempotencyKey("task-key");

        worker(tasks, rounds, messages, provision, access, properties).process(task);

        assertThat(task.getStatus()).isEqualTo("UNKNOWN");
        assertThat(task.getRetryCount()).isEqualTo(1);
        assertThat(task.getNextRetryAt()).isNotNull();
        verifyNoInteractions(access, provision, rounds, messages);
        verify(tasks).update(task);
    }

    @Test
    void retriesOnlyAfterEnabledReconciliationExplicitlyFindsNothing() {
        ConversationMemorySyncTaskDataService tasks = mock(ConversationMemorySyncTaskDataService.class);
        ConversationRoundService rounds = mock(ConversationRoundService.class);
        ConversationMessageService messages = mock(ConversationMessageService.class);
        ConversationMemoryProvisionService provision = mock(ConversationMemoryProvisionService.class);
        ConversationMemoryProviderAccess access = mock(ConversationMemoryProviderAccess.class);
        ConversationMemoryBindingEntity binding = binding();
        when(provision.findActive("tenant-a", 42L)).thenReturn(binding);
        when(access.findOwnedByExternalId(any(), eq("tenant-a"), eq(42L), eq(MemoryScope.SESSION),
                eq("session-1"), eq("task-key"))).thenReturn(null);
        ConversationMemoryProperties properties = properties();
        properties.getSync().setProviderIdempotencyEnabled(true);
        ConversationMemorySyncTaskEntity task = task("ADD_ROUND", "SESSION");
        task.setStatus("UNKNOWN");
        task.setIdempotencyKey("task-key");
        task.setTargetMemoryId("session-memory-1");

        worker(tasks, rounds, messages, provision, access, properties).process(task);

        assertThat(task.getStatus()).isEqualTo("RETRY");
        assertThat(task.getLastErrorCode()).isEqualTo("MEMORY_EXTERNAL_ID_NOT_FOUND");
        verify(access, org.mockito.Mockito.never()).addConversation(any(), any());
        verify(tasks).update(task);
    }

    @Test
    void keepsUnknownWhenProviderReconciliationFailsAndNeverWritesAgain() {
        ConversationMemorySyncTaskDataService tasks = mock(ConversationMemorySyncTaskDataService.class);
        ConversationRoundService rounds = mock(ConversationRoundService.class);
        ConversationMessageService messages = mock(ConversationMessageService.class);
        ConversationMemoryProvisionService provision = mock(ConversationMemoryProvisionService.class);
        ConversationMemoryProviderAccess access = mock(ConversationMemoryProviderAccess.class);
        ConversationMemoryBindingEntity binding = binding();
        when(provision.findActive("tenant-a", 42L)).thenReturn(binding);
        when(access.findOwnedByExternalId(binding, "tenant-a", 42L, MemoryScope.SESSION,
                "session-1", "task-key"))
                .thenThrow(new MemoryProviderException("LOOKUP_TIMEOUT", "lookup did not complete", true, null));
        ConversationMemoryProperties properties = properties();
        properties.getSync().setProviderIdempotencyEnabled(true);
        ConversationMemorySyncTaskEntity task = task("ADD_ROUND", "SESSION");
        task.setStatus("UNKNOWN");
        task.setTargetMemoryId("session-memory-1");
        task.setIdempotencyKey("task-key");

        worker(tasks, rounds, messages, provision, access, properties).process(task);

        assertThat(task.getStatus()).isEqualTo("UNKNOWN");
        assertThat(task.getLastErrorCode()).isEqualTo("LOOKUP_TIMEOUT");
        assertThat(task.getRetryCount()).isEqualTo(1);
        assertThat(task.getNextRetryAt()).isNotNull();
        verify(access).findOwnedByExternalId(binding, "tenant-a", 42L, MemoryScope.SESSION,
                "session-1", "task-key");
        verify(access, never()).addConversation(any(), any());
        verify(tasks).update(task);
    }

    @Test
    void deletesRetiredLongTermMemoryAndClearsItsBindingPointer() {
        ConversationMemorySyncTaskDataService tasks = mock(ConversationMemorySyncTaskDataService.class);
        ConversationRoundService rounds = mock(ConversationRoundService.class);
        ConversationMessageService messages = mock(ConversationMessageService.class);
        ConversationMemoryProvisionService provision = mock(ConversationMemoryProvisionService.class);
        ConversationMemoryProviderAccess access = mock(ConversationMemoryProviderAccess.class);
        ConversationMemoryBindingEntity binding = binding();
        binding.setRetiringLongTermMemoryId("retiring-long-memory-1");
        when(provision.findActive("tenant-a", 42L)).thenReturn(binding);
        when(provision.clearRetiringLongTermMemory(binding, "retiring-long-memory-1"))
                .thenReturn(true);
        ConversationMemorySyncTaskEntity task = task("DELETE_MEMORY", "LONG_TERM");
        task.setRoundCode(null);
        task.setTargetMemoryId("retiring-long-memory-1");

        worker(tasks, rounds, messages, provision, access).process(task);

        verify(access).deleteRetiredMemory(binding, "tenant-a", 42L, "retiring-long-memory-1");
        verify(provision).clearRetiringLongTermMemory(binding, "retiring-long-memory-1");
        assertThat(task.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(task.getLastErrorCode()).isNull();
        verify(tasks).update(task);
        verifyNoInteractions(rounds, messages);
    }

    private ConversationMemorySyncWorker worker(ConversationMemorySyncTaskDataService tasks,
                                                ConversationRoundService rounds,
                                                ConversationMessageService messages,
                                                ConversationMemoryProvisionService provision,
                                                ConversationMemoryProviderAccess access) {
        return worker(tasks, rounds, messages, provision, access, properties());
    }

    private ConversationMemorySyncWorker worker(ConversationMemorySyncTaskDataService tasks,
                                                ConversationRoundService rounds,
                                                ConversationMessageService messages,
                                                ConversationMemoryProvisionService provision,
                                                ConversationMemoryProviderAccess access,
                                                ConversationMemoryProperties properties) {
        return new ConversationMemorySyncWorker(tasks, rounds, messages, provision, access, properties);
    }

    private ConversationMemoryProperties properties() {
        ConversationMemoryProperties properties = new ConversationMemoryProperties();
        properties.setEnabled(true);
        properties.setIdentitySalt("test-salt");
        return properties;
    }

    private ConversationMemoryBindingEntity binding() {
        ConversationMemoryBindingEntity binding = new ConversationMemoryBindingEntity();
        binding.setTenantId("tenant-a");
        binding.setUserId(42L);
        binding.setStatus("ACTIVE");
        binding.setSchemaVersion(1);
        binding.setSessionMemoryId("session-memory-1");
        binding.setLongTermMemoryId("long-memory-1");
        return binding;
    }

    private ConversationMemorySyncTaskEntity task(String operation, String scope) {
        ConversationMemorySyncTaskEntity task = new ConversationMemorySyncTaskEntity();
        task.setId(1L);
        task.setTaskCode("task-1");
        task.setTenantId("tenant-a");
        task.setUserId(42L);
        task.setSessionCode("session-1");
        task.setRoundCode("round-1");
        task.setOperation(operation);
        task.setTargetScope(scope);
        task.setRetryCount(0);
        return task;
    }

    private ConversationRoundDTO round(String status) {
        ConversationRoundDTO round = new ConversationRoundDTO();
        round.setRoundCode("round-1");
        round.setSessionCode("session-1");
        round.setUserId(42L);
        round.setStatus(status);
        return round;
    }

    private ConversationMessageDTO message(String role, String content) {
        ConversationMessageDTO message = new ConversationMessageDTO();
        message.setRole(role);
        message.setSessionCode("session-1");
        message.setContent(content);
        return message;
    }

    private MemoryWriteResponse accepted(String messageId) {
        MemoryWriteResponse response = new MemoryWriteResponse();
        response.setAccepted(true);
        response.setProviderMessageId(messageId);
        return response;
    }
}
