package ai.platform.aiassist.service.ai.api;

import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbListRequest;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.athena.framework.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 本地知识库管理 API（数据库管理面）。
 *
 * <p>该接口主要用于服务内部通过 Feign 调用 aiEngine 模块，完成知识库、知识库文档的新增、更新、查询等管理操作。
 * 当前接口面向内部管理场景，不直接暴露给前端用户。</p>
 */
@FeignClient(
        name = "aiEngine",
        contextId = "platformAiKnowledgeBaseManageClient",
        path = "/aiEngine"
)
public interface AiKnowledgeBaseManageApi {

    /**
     * 新增或更新知识库文档。
     *
     * <p>当请求中的文档编码已存在时，更新对应文档内容；当文档编码不存在时，新增一条知识库文档记录。</p>
     *
     * @param request 知识库文档新增或更新请求参数，包含知识库编码、文档编码、标题、内容等信息
     * @return 文档新增或更新结果，包含文档编码、处理状态等信息
     */
    @PostMapping("/internal/v1/ai/kb/document/upsert")
    R<AiKbDocumentUpsertResponse> upsertDocument(@RequestBody AiKbDocumentUpsertRequest request);

    /**
     * 查询知识库列表。
     *
     * <p>用于获取当前系统中已维护的知识库基础信息，可根据请求参数进行条件过滤。</p>
     *
     * @param request 知识库列表查询条件；为空时查询默认范围内的知识库列表
     * @return 知识库信息列表
     */
    @PostMapping(value = "/internal/v1/ai/kb/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @IgnoredResultWrapper
    @Deprecated
    List<AiKbInfoDTO> kbList(@RequestBody(required = false) AiKbListRequest request);

    /**
     * 查询知识库文档列表。
     *
     * <p>根据知识库编码、文档标题、文档状态等条件查询文档概要信息，通常用于管理页面的文档列表展示。</p>
     *
     * @param request 知识库文档列表查询条件；为空时查询默认范围内的文档列表
     * @return 知识库文档列表项集合
     */
    @PostMapping(value = "/internal/v1/ai/kb/document/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @IgnoredResultWrapper
    @Deprecated
    List<AiKbDocumentListItemDTO> listDocuments(@RequestBody(required = false) AiKbDocumentListRequest request);

    /**
     * 查询知识库文档详情。
     *
     * <p>根据知识库编码和文档编码定位唯一文档，并返回文档标题、内容、元数据等详细信息。</p>
     *
     * @param kbCode 知识库编码
     * @param documentCode 文档编码
     * @return 知识库文档详情
     */
    @GetMapping(value = "/internal/v1/ai/kb/document/detail", produces = MediaType.APPLICATION_JSON_VALUE)
    @IgnoredResultWrapper
    @Deprecated
    AiKbDocumentDetailDTO getDocumentDetail(@RequestParam("kbCode") String kbCode, @RequestParam("documentCode") String documentCode);

}
