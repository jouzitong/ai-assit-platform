package ai.platform.aiassit.knowledge.manage.domainservice.impl;

import ai.platform.aiassit.execution.service.KnowledgeClientConfigService;
import ai.platform.aiassit.execution.service.KnowledgeClientOption;
import ai.platform.aiassit.knowledge.manage.domainservice.AiKnowledgeDatasetService;
import ai.platform.aiassit.knowledge.manage.entity.store.dto.AiKbStoreDTO;
import ai.platform.aiassit.knowledge.manage.service.AiKbStoreService;
import ai.platform.aiassit.service.ai.api.dto.AiKbAuthConfig;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.enums.AiKbStoreSyncStatus;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.BeanUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiKbStoreManageDomainServiceImplTest {

    private final AiKbStoreService storeService = mock(AiKbStoreService.class);
    private final KnowledgeClientConfigService clientConfigService = mock(KnowledgeClientConfigService.class);
    private final AiKnowledgeDatasetService datasetService = mock(AiKnowledgeDatasetService.class);
    private AiKbStoreManageDomainServiceImpl domainService;

    @BeforeEach
    void setUp() {
        KnowledgeClientOption option = new KnowledgeClientOption();
        option.setKey("ragflow");
        option.setClientType(AiKnowledgeClientType.RAGFLOW);
        when(clientConfigService.requireSingleOption()).thenReturn(option);
        when(clientConfigService.resolveAuth("ragflow")).thenReturn(new AiKbAuthConfig());
        domainService = new AiKbStoreManageDomainServiceImpl(storeService, clientConfigService, datasetService);
    }

    @Test
    void shouldSaveLocalCreatingStoreBeforeCreatingRemoteDatasetAndThenMarkActive() {
        AiKbDatasetDTO remote = dataset("dataset-remote-1");
        when(datasetService.createDataset(eq("ragflow"), any())).thenReturn(remote);
        when(storeService.add(any())).thenAnswer(invocation -> {
            AiKbStoreDTO store = invocation.getArgument(0);
            AiKbStoreDTO saved = copy(store);
            saved.setId(1L);
            return saved;
        });
        when(storeService.edit(eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(1));

        AiKbStoreDTO request = request();
        domainService.add(request);

        ArgumentCaptor<AiKbStoreDTO> addCaptor = ArgumentCaptor.forClass(AiKbStoreDTO.class);
        verify(storeService).add(addCaptor.capture());
        assertEquals(AiKbStoreSyncStatus.CREATING, addCaptor.getValue().getSyncStatus());
        assertEquals("one", addCaptor.getValue().getChunkMethod());

        ArgumentCaptor<AiKbStoreDTO> editCaptor = ArgumentCaptor.forClass(AiKbStoreDTO.class);
        verify(storeService).edit(eq(1L), editCaptor.capture());
        assertEquals("dataset-remote-1", editCaptor.getValue().getKbCode());
        assertEquals("dataset-remote-1", editCaptor.getValue().getProviderKbId());
        assertEquals(AiKbStoreSyncStatus.ACTIVE, editCaptor.getValue().getSyncStatus());

        InOrder order = inOrder(datasetService, storeService);
        order.verify(storeService).add(any());
        order.verify(datasetService).createDataset(eq("ragflow"), any());
        order.verify(storeService).edit(eq(1L), any());
    }

    @Test
    void shouldSaveLocalPendingStateBeforeRemoteUpdateAndDelete() {
        AiKbStoreDTO current = request();
        current.setId(1L);
        current.setKbCode("dataset-remote-1");
        current.setProviderKbId("dataset-remote-1");
        current.setChunkMethod("one");
        when(storeService.get(1L)).thenReturn(current);
        when(datasetService.updateDataset(eq("ragflow"), eq("dataset-remote-1"), any())).thenReturn(dataset("dataset-remote-1"));
        when(storeService.edit(eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(storeService.delete(1L)).thenReturn(true);

        domainService.edit(1L, new AiKbStoreDTO());
        domainService.delete(1L);

        InOrder order = inOrder(datasetService, storeService);
        order.verify(storeService).edit(eq(1L), any());
        order.verify(datasetService).updateDataset(eq("ragflow"), eq("dataset-remote-1"), any());
        order.verify(storeService).edit(eq(1L), any());
        order.verify(storeService).edit(eq(1L), any());
        order.verify(datasetService).deleteDatasets(eq("ragflow"), any());
        order.verify(storeService).delete(1L);
    }

    @Test
    void shouldKeepLocalFailedStateWhenRemoteCreateFails() {
        RuntimeException failure = new RuntimeException("ragflow unavailable");
        when(datasetService.createDataset(eq("ragflow"), any())).thenThrow(failure);
        when(storeService.add(any())).thenAnswer(invocation -> {
            AiKbStoreDTO store = invocation.getArgument(0);
            AiKbStoreDTO saved = copy(store);
            saved.setId(1L);
            return saved;
        });
        when(storeService.edit(eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(1));

        assertThrows(RuntimeException.class, () -> domainService.add(request()));

        ArgumentCaptor<AiKbStoreDTO> captor = ArgumentCaptor.forClass(AiKbStoreDTO.class);
        verify(storeService).edit(eq(1L), captor.capture());
        assertEquals(AiKbStoreSyncStatus.CREATE_FAILED, captor.getValue().getSyncStatus());
        assertEquals("ragflow unavailable", captor.getValue().getSyncError());
    }

    @Test
    void shouldRecreateRemoteDatasetWhenEditingCreateFailedLocalStoreWithoutProviderId() {
        AiKbStoreDTO current = request();
        current.setId(1L);
        current.setKbCode("local-1");
        current.setProviderKbId(null);
        current.setChunkMethod("one");
        current.setSyncStatus(AiKbStoreSyncStatus.CREATE_FAILED);
        when(storeService.get(1L)).thenReturn(current);
        when(datasetService.createDataset(eq("ragflow"), any())).thenReturn(dataset("dataset-remote-1"));
        when(storeService.edit(eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(1));

        domainService.edit(1L, new AiKbStoreDTO());

        verify(datasetService).createDataset(eq("ragflow"), any());
        verify(datasetService, never()).updateDataset(any(), any(), any());
    }

    @Test
    void shouldRejectMissingEmbeddingModelBeforeCallingRagflow() {
        AiKbStoreDTO request = request();
        request.setEmbeddingModel(null);

        assertThrows(RuntimeException.class, () -> domainService.add(request));

        verify(datasetService, never()).createDataset(any(), any());
        verify(storeService, never()).add(any());
    }

    @Test
    void shouldRejectInvalidEmbeddingModelBeforeCallingRagflow() {
        AiKbStoreDTO request = request();
        request.setEmbeddingModel("text-embedding-ada-002");

        assertThrows(RuntimeException.class, () -> domainService.add(request));

        verify(datasetService, never()).createDataset(any(), any());
        verify(storeService, never()).add(any());
    }

    private AiKbStoreDTO request() {
        AiKbStoreDTO request = new AiKbStoreDTO();
        request.setKbName("销售知识库");
        request.setEmbeddingModel("BAAI/bge-m3@BAAI");
        request.setEnabled(true);
        return request;
    }

    private AiKbDatasetDTO dataset(String id) {
        AiKbDatasetDTO dataset = new AiKbDatasetDTO();
        dataset.setKbId(id);
        return dataset;
    }

    private AiKbStoreDTO copy(AiKbStoreDTO source) {
        AiKbStoreDTO target = new AiKbStoreDTO();
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
