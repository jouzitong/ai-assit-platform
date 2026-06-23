package ai.platform.aiassist.service.ai.kb.controller;

import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentContentUpdateRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.AiKbCreateRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassist.service.ai.kb.controller.req.AiKbDeleteRequest;
import ai.platform.aiassist.service.ai.kb.controller.req.AiKbPageDocumentListRequest;
import ai.platform.aiassist.service.ai.kb.controller.req.AiKbSyncRequest;
import ai.platform.aiassist.service.ai.kb.controller.resp.AiKbDeleteResponse;
import ai.platform.aiassist.service.ai.kb.controller.resp.AiKbSyncResponse;
import org.athena.framework.web.vo.R;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 知识库页面 API（面向前端页面交互）。
 *
 * <p>该接口用于封装知识库页面相关的远程调用能力，包括知识库列表查询、
 * 文档列表查询、文档详情查询、文档新增/更新以及文档同步等操作。</p>
 *
 * <p>当前接口通过 Feign 调用 aiEngine 服务，统一以 /aiEngine 作为服务访问前缀。</p>
 */
public interface AiKnowledgeBasePageController {

    /**
     * 查询知识库列表。
     *
     * <p>用于前端页面初始化知识库下拉框、知识库管理列表等场景。</p>
     *
     * @return 知识库基础信息列表
     */
    @PostMapping(value = "/api/v1/ai/kb/list", produces = MediaType.APPLICATION_JSON_VALUE)
    List<AiKbInfoDTO> kbList();

    /**
     * 创建知识库主记录。
     *
     * @param request 知识库创建请求
     * @return 创建后的知识库信息
     */
    @PostMapping(value = "/api/v1/ai/kb/create", produces = MediaType.APPLICATION_JSON_VALUE)
    R<AiKbInfoDTO> createKnowledgeBase(@RequestBody AiKbCreateRequest request);

    /**
     * 查询知识库文档列表。
     *
     * <p>根据知识库编码、文档名称、状态等查询条件，获取指定知识库下的文档列表。</p>
     *
     * @param request 文档列表查询请求参数，允许为空；为空时由服务端使用默认查询条件
     * @return 知识库文档列表项集合
     */
    @PostMapping(value = "/api/v1/ai/kb/document/list", produces = MediaType.APPLICATION_JSON_VALUE)
    List<AiKbDocumentListItemDTO> listDocuments(@RequestBody(required = false) AiKbPageDocumentListRequest request);

    /**
     * 查询知识库文档详情。
     *
     * <p>用于文档详情页、文档编辑页回显文档基础信息、正文内容以及版本信息等数据。</p>
     *
     * @param kbCode 知识库编码，用于定位文档所属知识库
     * @param documentCode 文档编码，用于定位具体文档
     * @return 知识库文档详情信息
     */
    @GetMapping(value = "/api/v1/ai/kb/document/detail", produces = MediaType.APPLICATION_JSON_VALUE)
    AiKbDocumentDetailDTO getDocumentDetail(@RequestParam("kbCode") String kbCode,
                                            @RequestParam("documentCode") String documentCode);

    /**
     * 新增或更新知识库文档。
     *
     * <p>当请求中不存在文档编码或服务端判断为新文档时执行新增；
     * 当文档已存在时执行更新，用于统一处理文档保存操作。</p>
     *
     * @param request 文档新增或更新请求参数
     * @return 文档新增或更新结果，包含文档编码、处理状态等信息
     */
    @PostMapping("/api/v1/ai/kb/document/upsert")
    R<AiKbDocumentUpsertResponse> upsertDocument(@RequestBody AiKbDocumentUpsertRequest request);

    /**
     * 根据本地文档 ID 更新知识库文档正文。
     *
     * @param request 文档正文更新请求参数
     * @return 文档更新结果
     */
    @PostMapping("/api/v1/ai/kb/document/content/update")
    R<AiKbDocumentUpsertResponse> updateDocumentContent(@RequestBody AiKbDocumentContentUpdateRequest request);

    /**
     * 同步知识库文档。
     *
     * <p>用于将知识库文档内容同步到外部知识库、向量库或检索服务，
     * 使最新文档内容能够参与后续 AI 检索与问答。</p>
     *
     * @param request 文档同步请求参数
     * @return 文档同步结果，包含同步状态、失败原因等信息
     */
    @PostMapping("/api/v1/ai/kb/document/sync")
    R<AiKbSyncResponse> syncDocument(@RequestBody AiKbSyncRequest request);

    @PostMapping("/api/v1/ai/kb/document/delete")
    R<AiKbDeleteResponse> deleteDocument(@RequestBody AiKbDeleteRequest request);
}
