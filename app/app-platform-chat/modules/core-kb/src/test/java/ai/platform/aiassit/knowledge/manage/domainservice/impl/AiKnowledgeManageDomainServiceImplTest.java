package ai.platform.aiassit.knowledge.manage.domainservice.impl;

import ai.platform.aiassit.execution.service.AiKnowledgeExecutionService;
import ai.platform.aiassit.knowledge.manage.domainservice.AiKbStoreManageDomainService;
import ai.platform.aiassit.knowledge.manage.entity.document.dto.AiKbDocumentContentDTO;
import ai.platform.aiassit.knowledge.manage.entity.document.dto.AiKbDocumentDTO;
import ai.platform.aiassit.knowledge.manage.entity.store.dto.AiKbStoreDTO;
import ai.platform.aiassit.knowledge.manage.vo.AiKbStoreVO;
import ai.platform.aiassit.knowledge.manage.entity.task.dto.AiKbPublishTaskDTO;
import ai.platform.aiassit.knowledge.manage.req.AiKbDocumentStatusUpdateRequest;
import ai.platform.aiassit.knowledge.manage.req.AiKbSyncRequest;
import ai.platform.aiassit.knowledge.manage.service.AiKbDocumentContentService;
import ai.platform.aiassit.knowledge.manage.service.AiKbDocumentService;
import ai.platform.aiassit.knowledge.manage.service.AiKbDocumentVersionContentService;
import ai.platform.aiassit.knowledge.manage.service.AiKbDocumentVersionService;
import ai.platform.aiassit.knowledge.manage.service.AiKbPublishTaskService;
import ai.platform.aiassit.knowledge.manage.service.AiKbStoreService;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.KbDocument;
import ai.platform.aiassit.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbCreateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassit.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassit.service.ai.api.enums.AiKbContentFormat;
import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentStatus;
import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentType;
import ai.platform.aiassit.service.ai.api.enums.AiKbProviderSyncStatus;
import ai.platform.aiassit.service.ai.api.enums.AiKbStoreSyncStatus;
import org.arthena.framework.common.thread.AsyncTaskExcutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiKnowledgeManageDomainServiceImplTest {

    private final AiKbStoreService storeService = mock(AiKbStoreService.class);
    private final AiKbDocumentService documentService = mock(AiKbDocumentService.class);
    private final AiKbDocumentContentService contentService = mock(AiKbDocumentContentService.class);
    private final AiKbDocumentVersionService documentVersionService = mock(AiKbDocumentVersionService.class);
    private final AiKbDocumentVersionContentService documentVersionContentService = mock(AiKbDocumentVersionContentService.class);
    private final AiKnowledgeExecutionService aiKnowledgeExecutionService = mock(AiKnowledgeExecutionService.class);
    private final AiKbPublishTaskService publishTaskService = mock(AiKbPublishTaskService.class);
    private final AiKbStoreManageDomainService storeManageDomainService = mock(AiKbStoreManageDomainService.class);
    private final AsyncTaskExcutor directExecutor = mock(AsyncTaskExcutor.class);
    private AiKnowledgeManageDomainServiceImpl domainService;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return CompletableFuture.completedFuture(null);
        }).when(directExecutor).submit(any(Runnable.class));
        domainService = new AiKnowledgeManageDomainServiceImpl(
                storeService,
                documentService,
                contentService,
                documentVersionService,
                documentVersionContentService,
                aiKnowledgeExecutionService,
                publishTaskService,
                storeManageDomainService,
                directExecutor);
        when(publishTaskService.add(any())).thenAnswer(invocation -> {
            AiKbPublishTaskDTO task = invocation.getArgument(0);
            task.setId(100L);
            return task;
        });
        when(publishTaskService.update(eq(100L), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(storeService.update(eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void shouldPassRequestedKbCodeWhenCreatingKnowledgeBase() {
        AiKbCreateRequest request = new AiKbCreateRequest();
        request.setKbCode("sales-knowledge");
        request.setKbName("销售知识库");
        request.setEmbeddingModel("BAAI/bge-m3@BAAI");

        AiKbStoreVO saved = new AiKbStoreVO();
        saved.setId(1L);
        saved.setKbCode("sales-knowledge");
        saved.setProviderKbId("dataset-1");
        when(storeManageDomainService.add(any())).thenReturn(saved);

        AiKbInfoDTO result = domainService.createKnowledgeBase(request);

        ArgumentCaptor<AiKbStoreDTO> storeCaptor = ArgumentCaptor.forClass(AiKbStoreDTO.class);
        verify(storeManageDomainService).add(storeCaptor.capture());
        assertEquals("sales-knowledge", storeCaptor.getValue().getKbCode());
        assertEquals("sales-knowledge", result.getKbId());
    }

    @Test
    void shouldSwitchLocalProviderDocumentIdAfterRemoteChunkSucceedsThenDeleteOldDocument() {
        AiKbDocumentDTO document = document("old-doc", AiKbProviderSyncStatus.PENDING);
        AiKbDocumentContentDTO content = content("新版内容");
        mockSyncInputs(document, content);
        KbUpsertResponse response = upsertResponse("new-doc");
        when(aiKnowledgeExecutionService.kbUpsert(any())).thenReturn(response);

        domainService.syncDocument(syncRequest());

        ArgumentCaptor<KbUpsertRequest> upsertCaptor = ArgumentCaptor.forClass(KbUpsertRequest.class);
        verify(aiKnowledgeExecutionService).kbUpsert(upsertCaptor.capture());
        KbDocument uploadedDocument = upsertCaptor.getValue().getDocuments().get(0);
        assertEquals("old-doc", uploadedDocument.getMetadata().get("providerDocumentId"));

        ArgumentCaptor<KbDeleteRequest> deleteCaptor = ArgumentCaptor.forClass(KbDeleteRequest.class);
        verify(aiKnowledgeExecutionService).kbDelete(deleteCaptor.capture());
        assertEquals(List.of("old-doc"), deleteCaptor.getValue().getDocumentIds());

        ArgumentCaptor<AiKbDocumentDTO> documentCaptor = ArgumentCaptor.forClass(AiKbDocumentDTO.class);
        verify(documentService, atLeastOnce()).update(eq(1L), documentCaptor.capture());
        AiKbDocumentDTO finalDocument = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1);
        assertEquals("new-doc", finalDocument.getProviderDocumentId());
        assertEquals(AiKbProviderSyncStatus.SUCCESS, finalDocument.getProviderSyncStatus());

        ArgumentCaptor<AiKbDocumentContentDTO> contentCaptor = ArgumentCaptor.forClass(AiKbDocumentContentDTO.class);
        verify(contentService).update(eq(10L), contentCaptor.capture());
        Map<String, Object> ext = contentCaptor.getValue().getExtJson();
        assertEquals("new-doc", ext.get("lastSyncProviderDocumentId"));
        assertFalse(ext.containsKey("pendingDeleteProviderDocumentIds"));
    }

    @Test
    void shouldKeepProviderDocumentIdWhenProviderUpdatesDocumentInPlace() {
        AiKbDocumentDTO document = document("old-doc", AiKbProviderSyncStatus.PENDING);
        mockSyncInputs(document, content("新版内容"));
        when(aiKnowledgeExecutionService.kbUpsert(any())).thenReturn(upsertResponse("old-doc"));

        domainService.syncDocument(syncRequest());

        verify(aiKnowledgeExecutionService, never()).kbDelete(any());
        ArgumentCaptor<AiKbDocumentDTO> documentCaptor = ArgumentCaptor.forClass(AiKbDocumentDTO.class);
        verify(documentService, atLeastOnce()).update(eq(1L), documentCaptor.capture());
        AiKbDocumentDTO finalDocument = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1);
        assertEquals("old-doc", finalDocument.getProviderDocumentId());
        assertEquals(AiKbProviderSyncStatus.SUCCESS, finalDocument.getProviderSyncStatus());
    }

    @Test
    void shouldKeepOldProviderDocumentWhenRemoteChunkFails() {
        AiKbDocumentDTO document = document("old-doc", AiKbProviderSyncStatus.PENDING);
        mockSyncInputs(document, content("新版内容"));
        when(aiKnowledgeExecutionService.kbUpsert(any())).thenThrow(new RuntimeException("chunk failed"));

        domainService.syncDocument(syncRequest());

        verify(aiKnowledgeExecutionService, never()).kbDelete(any());
        ArgumentCaptor<AiKbDocumentDTO> documentCaptor = ArgumentCaptor.forClass(AiKbDocumentDTO.class);
        verify(documentService, atLeastOnce()).update(eq(1L), documentCaptor.capture());
        AiKbDocumentDTO finalDocument = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1);
        assertEquals("old-doc", finalDocument.getProviderDocumentId());
        assertEquals(AiKbProviderSyncStatus.FAILED, finalDocument.getProviderSyncStatus());
        assertEquals("chunk failed", finalDocument.getLastError());
    }

    @Test
    void shouldCleanupNewProviderDocumentWhenLocalSwitchFails() {
        AiKbDocumentDTO document = document("old-doc", AiKbProviderSyncStatus.PENDING);
        mockSyncInputs(document, content("新版内容"));
        when(aiKnowledgeExecutionService.kbUpsert(any())).thenReturn(upsertResponse("new-doc"));
        AtomicInteger updateCount = new AtomicInteger();
        when(documentService.update(eq(1L), any())).thenAnswer(invocation -> {
            if (updateCount.incrementAndGet() == 2) {
                throw new RuntimeException("db update failed");
            }
            return invocation.getArgument(1);
        });

        domainService.syncDocument(syncRequest());

        ArgumentCaptor<KbDeleteRequest> deleteCaptor = ArgumentCaptor.forClass(KbDeleteRequest.class);
        verify(aiKnowledgeExecutionService).kbDelete(deleteCaptor.capture());
        assertEquals(List.of("new-doc"), deleteCaptor.getValue().getDocumentIds());

        ArgumentCaptor<AiKbDocumentDTO> documentCaptor = ArgumentCaptor.forClass(AiKbDocumentDTO.class);
        verify(documentService, atLeastOnce()).update(eq(1L), documentCaptor.capture());
        AiKbDocumentDTO finalDocument = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1);
        assertEquals("old-doc", finalDocument.getProviderDocumentId());
        assertEquals(AiKbProviderSyncStatus.FAILED, finalDocument.getProviderSyncStatus());
    }

    @Test
    void shouldUpdateStatusForSelectedDocuments() {
        AiKbDocumentDTO document = document("provider-doc", AiKbProviderSyncStatus.SUCCESS);
        AiKbDocumentStatusUpdateRequest request = new AiKbDocumentStatusUpdateRequest();
        request.setKbCode("kb-1");
        request.setDocumentCodes(List.of("doc-1"));
        request.setEnabled(false);
        when(documentService.listByQuery(any())).thenReturn(List.of(document));

        int updatedCount = domainService.updateDocumentStatus(request);

        assertEquals(1, updatedCount);
        ArgumentCaptor<AiKbDocumentDTO> documentCaptor = ArgumentCaptor.forClass(AiKbDocumentDTO.class);
        verify(documentService).update(eq(1L), documentCaptor.capture());
        assertEquals(AiKbDocumentStatus.DISABLED, documentCaptor.getValue().getStatus());
    }

    @Test
    void shouldCreateDisabledDocumentWhenUpsertExplicitlyRequestsDraft() {
        AiKbStoreDTO store = new AiKbStoreDTO();
        store.setId(1L);
        store.setKbCode("kb-1");
        store.setProviderKbId("dataset-1");
        store.setEnabled(true);
        when(storeService.getByKbCode("kb-1")).thenReturn(store);
        when(documentService.add(any())).thenAnswer(invocation -> {
            AiKbDocumentDTO document = invocation.getArgument(0);
            document.setId(1L);
            return document;
        });

        AiKbDocumentUpsertRequest request = new AiKbDocumentUpsertRequest();
        request.setKbCode("kb-1");
        request.setDocumentId("virtual-table/1");
        request.setDocumentName("订单");
        request.setDocumentType(AiKbDocumentType.DB_TABLE);
        request.setBizType(AiKbBizType.DB_DATA_SOURCE);
        request.setContent("# 订单");
        request.setCanUpdate(false);
        request.setEnabled(false);

        domainService.upsertDocument(request);

        ArgumentCaptor<AiKbDocumentDTO> documentCaptor = ArgumentCaptor.forClass(AiKbDocumentDTO.class);
        verify(documentService).add(documentCaptor.capture());
        assertEquals(AiKbDocumentStatus.DISABLED, documentCaptor.getValue().getStatus());
        assertEquals(AiKbProviderSyncStatus.PENDING, documentCaptor.getValue().getProviderSyncStatus());
    }

    private void mockSyncInputs(AiKbDocumentDTO document, AiKbDocumentContentDTO content) {
        AiKbStoreDTO store = new AiKbStoreDTO();
        store.setId(1L);
        store.setKbCode("kb-1");
        store.setProviderKbId("dataset-1");
        store.setEnabled(true);
        store.setSyncStatus(AiKbStoreSyncStatus.ACTIVE);
        store.setExtJson(new LinkedHashMap<>());
        when(storeService.getByKbCode("kb-1")).thenReturn(store);
        when(documentService.listByQuery(any())).thenReturn(List.of(document));
        when(contentService.getByDocumentId(1L)).thenReturn(content);
    }

    private AiKbSyncRequest syncRequest() {
        AiKbSyncRequest request = new AiKbSyncRequest();
        request.setKbCode("kb-1");
        request.setDocumentCodes(List.of("doc-1"));
        request.setForce(true);
        return request;
    }

    private KbUpsertResponse upsertResponse(String providerDocumentId) {
        KbUpsertResponse response = new KbUpsertResponse();
        response.setKbId("dataset-1");
        response.setAccepted(1);
        response.setFailed(0);
        response.setDocumentIdMappings(Map.of("doc-1", providerDocumentId));
        return response;
    }

    private AiKbDocumentDTO document(String providerDocumentId, AiKbProviderSyncStatus syncStatus) {
        AiKbDocumentDTO document = new AiKbDocumentDTO();
        document.setId(1L);
        document.setKbCode("kb-1");
        document.setDocumentCode("doc-1");
        document.setDocumentName("指标说明");
        document.setDocumentType(AiKbDocumentType.DB_TABLE);
        document.setBizType(AiKbBizType.DB_DATA_SOURCE);
        document.setBizKey("doc-1");
        document.setStatus(AiKbDocumentStatus.ACTIVE);
        document.setProviderDocumentId(providerDocumentId);
        document.setProviderSyncStatus(syncStatus);
        document.setDocumentVersionNo(2);
        document.setContentChecksum("checksum-new");
        document.setContentFormat(AiKbContentFormat.MARKDOWN);
        document.setContentSize(12L);
        document.setMetaJson(new LinkedHashMap<>());
        return document;
    }

    private AiKbDocumentContentDTO content(String renderedContent) {
        AiKbDocumentContentDTO content = new AiKbDocumentContentDTO();
        content.setId(10L);
        content.setDocumentId(1L);
        content.setContentFormat(AiKbContentFormat.MARKDOWN);
        content.setContentSize(12L);
        content.setRenderedContent(renderedContent);
        content.setExtJson(new LinkedHashMap<>());
        return content;
    }
}
