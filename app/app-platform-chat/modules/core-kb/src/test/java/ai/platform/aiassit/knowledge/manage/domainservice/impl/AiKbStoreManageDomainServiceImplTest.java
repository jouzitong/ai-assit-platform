package ai.platform.aiassit.knowledge.manage.domainservice.impl;

import ai.platform.aiassit.execution.service.KnowledgeClientConfigService;
import ai.platform.aiassit.execution.service.KnowledgeClientOption;
import ai.platform.aiassit.knowledge.manage.domainservice.AiKnowledgeDatasetService;
import ai.platform.aiassit.knowledge.manage.entity.store.dto.AiKbStoreDTO;
import ai.platform.aiassit.knowledge.manage.service.AiKbStoreService;
import ai.platform.aiassit.service.ai.api.dto.AiKbAuthConfig;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

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
    void shouldCreateRemoteDatasetBeforeSavingLocalStoreAndUseRemoteIdAsCode() {
        AiKbDatasetDTO remote = dataset("dataset-remote-1");
        when(datasetService.createDataset(eq("ragflow"), any())).thenReturn(remote);
        when(storeService.add(any())).thenAnswer(invocation -> {
            AiKbStoreDTO store = invocation.getArgument(0);
            store.setId(1L);
            return store;
        });

        AiKbStoreDTO request = request();
        domainService.add(request);

        ArgumentCaptor<AiKbStoreDTO> captor = ArgumentCaptor.forClass(AiKbStoreDTO.class);
        verify(storeService).add(captor.capture());
        assertEquals("dataset-remote-1", captor.getValue().getKbCode());
        assertEquals("dataset-remote-1", captor.getValue().getProviderKbId());
        assertEquals("one", captor.getValue().getChunkMethod());

        InOrder order = inOrder(datasetService, storeService);
        order.verify(datasetService).createDataset(eq("ragflow"), any());
        order.verify(storeService).add(any());
    }

    @Test
    void shouldSynchronizeRemoteDatasetBeforeEveryLocalUpdateAndDelete() {
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
        order.verify(datasetService).updateDataset(eq("ragflow"), eq("dataset-remote-1"), any());
        order.verify(storeService).edit(eq(1L), any());
        order.verify(datasetService).deleteDatasets(eq("ragflow"), any());
        order.verify(storeService).delete(1L);
    }

    @Test
    void shouldRejectMissingEmbeddingModelBeforeCallingRagflow() {
        AiKbStoreDTO request = request();
        request.setEmbeddingModel(null);

        assertThrows(RuntimeException.class, () -> domainService.add(request));

        verify(datasetService, never()).createDataset(any(), any());
        verify(storeService, never()).add(any());
    }

    private AiKbStoreDTO request() {
        AiKbStoreDTO request = new AiKbStoreDTO();
        request.setKbName("销售知识库");
        request.setEmbeddingModel("BAAI/bge-m3");
        request.setEnabled(true);
        return request;
    }

    private AiKbDatasetDTO dataset(String id) {
        AiKbDatasetDTO dataset = new AiKbDatasetDTO();
        dataset.setKbId(id);
        return dataset;
    }
}
