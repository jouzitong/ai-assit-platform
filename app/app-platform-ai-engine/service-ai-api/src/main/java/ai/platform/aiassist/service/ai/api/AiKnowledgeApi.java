package ai.platform.aiassist.service.ai.api;

import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentContentUpdateRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.athena.framework.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AI 知识库 API（HTTP/Feign）。
 *
 * <p>知识库写入统一走本地管理面，先在 aiEngine 保存文档与版本，再由内部服务同步到模型提供方；
 * 删除和检索保留执行面能力，直接委托底层知识库服务。</p>
 */
@FeignClient(
        name = "aiEngine",
        contextId = "platformAiKnowledgeClient",
        path = "/aiEngine"
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

    /**
     * 根据知识库条件获取本地知识库标识。
     *
     * @param request 知识库查询条件；可按业务类型、来源唯一键、启用状态过滤
     * @return 匹配到的本地知识库标识；无匹配时 data 为空
     */
    @PostMapping("/internal/v1/ai/kb/id")
    R<String> getKbId(@RequestBody(required = false) AiKbListRequest request);

    @PostMapping("/api/v1/ai/execution/kb/delete")
    @IgnoredResultWrapper
    KbDeleteResponse kbDelete(@RequestBody KbDeleteRequest request);

    @PostMapping("/api/v1/ai/execution/kb/search")
    @IgnoredResultWrapper
    KbSearchResponse kbSearch(@RequestBody KbSearchRequest request);
}
