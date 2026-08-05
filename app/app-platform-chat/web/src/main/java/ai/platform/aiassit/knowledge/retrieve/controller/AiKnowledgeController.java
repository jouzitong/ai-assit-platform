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

/**
 * 面向平台内部调用的知识库管理与检索接口。
 *
 * <p>文档写入统一落在本地管理面后再同步提供方；Dataset 管理与检索执行通过已配置的知识库客户端完成，调用方不直接持有凭据。</p>
 */
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

    /**
     * 新增或更新本地知识库文档。
     *
     * @param request 文档保存请求体，包含知识库编码、稳定文档编码、内容和业务属性
     * @return 包装后的保存结果，包含文档处理状态
     */
    @Override
    public R<AiKbDocumentUpsertResponse> upsertDocument(@RequestBody AiKbDocumentUpsertRequest request) {
        return R.ok(domainService.upsertDocument(request));
    }

    /**
     * 根据本地文档定位信息更新文档正文。
     *
     * @param request 正文更新请求体，包含本地文档标识和新内容
     * @return 包装后的更新结果
     */
    @Override
    public R<AiKbDocumentUpsertResponse> updateDocumentContent(@RequestBody AiKbDocumentContentUpdateRequest request) {
        return R.ok(domainService.updateDocumentContent(request));
    }

    /**
     * 按稳定文档编码批量查询本地文档摘要。
     *
     * @param request 批量查询请求体，包含待查询的文档编码集合
     * @return 包装后的文档摘要列表
     */
    @Override
    public R<List<AiKbDocumentListItemDTO>> listDocuments(@RequestBody AiKbDocumentBatchRequest request) {
        return R.ok(domainService.listDocumentsByCodes(request));
    }

    /**
     * 删除多知识库中的本地文档及其提供方侧内容。
     *
     * <p>接口先按文档所属知识库分组，再逐个执行删除，从而汇总成功数量和因状态或归属不满足条件而跳过的文档。</p>
     *
     * @param request 批量删除请求体，包含稳定文档编码集合
     * @return 包装后的删除结果，包含已删除数量和跳过的文档编码
     */
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

    /**
     * 根据可选条件获取一个可用的本地知识库标识。
     *
     * @param request 可选查询请求体，可按启用状态等条件筛选
     * @return 包装后的本地知识库标识；没有匹配项时数据为空
     */
    @Override
    public R<String> getKbId(@RequestBody(required = false) AiKbListRequest request) {
        return R.ok(domainService.getKbId(request));
    }

    /**
     * 查询已配置知识库客户端在提供方侧的 Dataset 列表。
     *
     * @param request 可选查询请求体，用于提供方侧筛选或分页
     * @return 包装后的远端 Dataset 列表
     */
    @Override
    public R<List<AiKbDatasetDTO>> listDatasets(@RequestBody(required = false) AiKbDatasetListRequest request) {
        return R.ok(datasetService.listDatasets(request));
    }

    /**
     * 查询已配置知识库客户端支持的 Embedding 模型。
     *
     * @param request 可选查询请求体，用于筛选可用向量模型
     * @return 包装后的 Embedding 模型列表
     */
    @Override
    public R<List<AiKbEmbeddingModelDTO>> listEmbeddingModels(@RequestBody(required = false) AiKbEmbeddingModelListRequest request) {
        return R.ok(datasetService.listEmbeddingModels(request));
    }

    /**
     * 在已配置客户端的提供方侧创建 Dataset。
     *
     * @param request Dataset 保存请求体，包含名称、描述和向量化配置
     * @return 包装后的新建 Dataset 信息
     */
    @Override
    public R<AiKbDatasetDTO> createDataset(@RequestBody AiKbDatasetSaveRequest request) {
        return R.ok(datasetService.createDataset(request));
    }

    /**
     * 更新提供方侧 Dataset 的可编辑配置。
     *
     * @param kbId    提供方 Dataset 标识
     * @param request Dataset 保存请求体，包含待更新配置
     * @return 包装后的 Dataset 更新结果
     */
    @Override
    public R<AiKbDatasetDTO> updateDataset(@PathVariable String kbId, @RequestBody AiKbDatasetSaveRequest request) {
        return R.ok(datasetService.updateDataset(kbId, request));
    }

    /**
     * 删除提供方侧一个或多个 Dataset。
     *
     * @param request 删除请求体，包含待删除的 Dataset 标识集合
     * @return 包装后的实际删除数量
     */
    @Override
    public R<Integer> deleteDatasets(@RequestBody AiKbDatasetDeleteRequest request) {
        return R.ok(datasetService.deleteDatasets(request));
    }

    /**
     * 在指定知识库中删除已同步的提供方文档。
     *
     * @param request 删除执行请求体，包含知识库定位、文档标识和调用元数据
     * @return 提供方删除结果，经执行服务完成配置与认证合并
     */
    @Override
    public KbDeleteResponse kbDelete(@RequestBody KbDeleteRequest request) {
        return aiKnowledgeExecutionService.kbDelete(request);
    }

    /**
     * 在指定知识库中执行文档检索。
     *
     * @param request 检索请求体，包含知识库、查询文本、召回数量和调用元数据
     * @return 检索结果，返回本地知识库编码而非暴露提供方 Dataset 标识
     */
    @Override
    public KbSearchResponse kbSearch(@RequestBody KbSearchRequest request) {
        return aiKnowledgeExecutionService.kbSearch(request);
    }
}
