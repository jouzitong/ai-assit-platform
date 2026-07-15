package ai.platform.aiassit.service.ai.api;

import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetSaveRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbEmbeddingModelDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbEmbeddingModelListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentContentUpdateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentBatchRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentDeleteResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassit.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassit.service.ai.api.dto.KbSearchResponse;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.athena.framework.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * AI 知识库 API（HTTP/Feign）。
 *
 * <p>知识库写入统一走本地管理面，先在 aiEngine 保存文档与版本，再由内部服务同步到模型提供方；
 * 删除和检索保留执行面能力，直接委托底层知识库服务。</p>
 */
@FeignClient(
        name = "chat",
        contextId = "platformChatKnowledgeClient",
        path = "/chat"
)
public interface AiKnowledgeApi {

    /**
     * 新增或更新知识库文档。
     *
     * <p>当请求中的文档编码已存在时，更新对应文档内容；当文档编码不存在时，新增一条知识库文档记录。
     * 业务类型优先使用请求中的 bizType；未传时按 documentType 的默认业务类型推导。</p>
     *
     * @param request 知识库文档新增或更新请求参数，包含知识库编码、文档编码、业务类型、标题、内容等信息
     * @return 文档新增或更新结果，包含文档编码、处理状态等信息
     */
    @PostMapping("/internal/v1/ai/kb/document/upsert")
    R<AiKbDocumentUpsertResponse> upsertDocument(@RequestBody AiKbDocumentUpsertRequest request);

    /**
     * 根据本地文档 ID 更新知识库文档正文。
     *
     * @param request 文档正文更新请求参数
     * @return 文档更新结果
     */
    @PostMapping("/internal/v1/ai/kb/document/content/update")
    R<AiKbDocumentUpsertResponse> updateDocumentContent(@RequestBody AiKbDocumentContentUpdateRequest request);

    /** 按稳定文档编码批量查询本地知识库文档。 */
    @PostMapping("/internal/v1/ai/kb/document/list")
    R<List<AiKbDocumentListItemDTO>> listDocuments(@RequestBody AiKbDocumentBatchRequest request);

    /** 删除本地知识库文档及其已同步到 Provider 的文档。 */
    @PostMapping("/internal/v1/ai/kb/document/delete")
    R<AiKbDocumentDeleteResponse> deleteDocuments(@RequestBody AiKbDocumentBatchRequest request);

    /**
     * 根据知识库条件获取本地知识库标识。
     *
     * @param request 知识库查询条件；可按启用状态过滤
     * @return 匹配到的本地知识库标识；无匹配时 data 为空
     */
    @PostMapping("/internal/v1/ai/kb/id")
    R<String> getKbId(@RequestBody(required = false) AiKbListRequest request);

    /**
     * 查询知识库提供方侧的 Dataset 列表。
     *
     * <p>返回项中的 {@code kbId} 即 RAGFlow Dataset ID，可用于后续文档写入与检索。</p>
     */
    @PostMapping("/internal/v1/ai/kb/dataset/list")
    R<List<AiKbDatasetDTO>> listDatasets(@RequestBody(required = false) AiKbDatasetListRequest request);

    /** 查询知识库提供方侧可用于 Dataset 的 Embedding 模型。 */
    @PostMapping("/internal/v1/ai/kb/embedding-model/list")
    R<List<AiKbEmbeddingModelDTO>> listEmbeddingModels(@RequestBody(required = false) AiKbEmbeddingModelListRequest request);

    /** 创建知识库提供方侧的 Dataset。 */
    @PostMapping("/internal/v1/ai/kb/dataset")
    R<AiKbDatasetDTO> createDataset(@RequestBody AiKbDatasetSaveRequest request);

    /** 更新知识库提供方侧的 Dataset。 */
    @PutMapping("/internal/v1/ai/kb/dataset/{kbId}")
    R<AiKbDatasetDTO> updateDataset(@PathVariable String kbId, @RequestBody AiKbDatasetSaveRequest request);

    /** 删除知识库提供方侧的一个或多个 Dataset。 */
    @DeleteMapping("/internal/v1/ai/kb/dataset")
    R<Integer> deleteDatasets(@RequestBody AiKbDatasetDeleteRequest request);

    @PostMapping("/api/v1/ai/execution/kb/delete")
    @IgnoredResultWrapper
    KbDeleteResponse kbDelete(@RequestBody KbDeleteRequest request);

    @PostMapping("/api/v1/ai/execution/kb/search")
    @IgnoredResultWrapper
    KbSearchResponse kbSearch(@RequestBody KbSearchRequest request);
}
