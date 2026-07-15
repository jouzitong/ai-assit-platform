package ai.platform.aiassit.knowledge.retrieve.controller;

import ai.platform.aiassit.service.ai.api.AiKnowledgeApi;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentContentUpdateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentBatchRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentDeleteResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetSaveRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbEmbeddingModelDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbEmbeddingModelListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassit.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassit.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassit.execution.service.AiKnowledgeExecutionService;
import ai.platform.aiassit.knowledge.manage.domainservice.AiKnowledgeManageDomainService;
import ai.platform.aiassit.knowledge.manage.domainservice.AiKnowledgeDatasetService;
import ai.platform.aiassit.knowledge.manage.req.AiKbDeleteRequest;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AiKnowledgeController implements AiKnowledgeApi {

    private final AiKnowledgeManageDomainService domainService;
    private final AiKnowledgeDatasetService datasetService;
    private final AiKnowledgeExecutionService aiKnowledgeExecutionService;

    public AiKnowledgeController(AiKnowledgeManageDomainService domainService,
                                 AiKnowledgeDatasetService datasetService,
                                 AiKnowledgeExecutionService aiKnowledgeExecutionService) {
        this.domainService = domainService;
        this.datasetService = datasetService;
        this.aiKnowledgeExecutionService = aiKnowledgeExecutionService;
    }

    @Override
    public R<AiKbDocumentUpsertResponse> upsertDocument(@RequestBody AiKbDocumentUpsertRequest request) {
        return R.ok(domainService.upsertDocument(request));
    }

    @Override
    public R<AiKbDocumentUpsertResponse> updateDocumentContent(@RequestBody AiKbDocumentContentUpdateRequest request) {
        return R.ok(domainService.updateDocumentContent(request));
    }

    @Override
    public R<List<AiKbDocumentListItemDTO>> listDocuments(@RequestBody AiKbDocumentBatchRequest request) {
        return R.ok(domainService.listDocumentsByCodes(request));
    }

    @Override
    public R<AiKbDocumentDeleteResponse> deleteDocuments(@RequestBody AiKbDocumentBatchRequest request) {
        List<AiKbDocumentListItemDTO> documents = listDocuments(request).getData();
        Map<String, List<String>> codesByKb = new LinkedHashMap<>();
        documents.forEach(item -> codesByKb.computeIfAbsent(item.getKbCode(), ignored -> new ArrayList<>()).add(item.getDocumentCode()));

        AiKbDocumentDeleteResponse response = new AiKbDocumentDeleteResponse();
        codesByKb.forEach((kbCode, documentCodes) -> {
            AiKbDeleteRequest deleteRequest = new AiKbDeleteRequest();
            deleteRequest.setKbCode(kbCode);
            deleteRequest.setDocumentCodes(documentCodes);
            ai.platform.aiassit.knowledge.manage.resp.AiKbDeleteResponse result = domainService.deleteDocument(deleteRequest);
            response.setDeletedCount(response.getDeletedCount() + result.getDeletedCount());
            response.getSkippedDocumentCodes().addAll(result.getSkippedDocumentCodes());
        });
        return R.ok(response);
    }

    @Override
    public R<String> getKbId(@RequestBody(required = false) AiKbListRequest request) {
        return R.ok(domainService.getKbId(request));
    }

    @Override
    public R<List<AiKbDatasetDTO>> listDatasets(@RequestBody(required = false) AiKbDatasetListRequest request) {
        return R.ok(datasetService.listDatasets(request));
    }

    @Override
    public R<List<AiKbEmbeddingModelDTO>> listEmbeddingModels(@RequestBody(required = false) AiKbEmbeddingModelListRequest request) {
        return R.ok(datasetService.listEmbeddingModels(request));
    }

    @Override
    public R<AiKbDatasetDTO> createDataset(@RequestBody AiKbDatasetSaveRequest request) {
        return R.ok(datasetService.createDataset(request));
    }

    @Override
    public R<AiKbDatasetDTO> updateDataset(@PathVariable String kbId, @RequestBody AiKbDatasetSaveRequest request) {
        return R.ok(datasetService.updateDataset(kbId, request));
    }

    @Override
    public R<Integer> deleteDatasets(@RequestBody AiKbDatasetDeleteRequest request) {
        return R.ok(datasetService.deleteDatasets(request));
    }

    @Override
    public KbDeleteResponse kbDelete(@RequestBody KbDeleteRequest request) {
        return aiKnowledgeExecutionService.kbDelete(request);
    }

    @Override
    public KbSearchResponse kbSearch(@RequestBody KbSearchRequest request) {
        return aiKnowledgeExecutionService.kbSearch(request);
    }
}
