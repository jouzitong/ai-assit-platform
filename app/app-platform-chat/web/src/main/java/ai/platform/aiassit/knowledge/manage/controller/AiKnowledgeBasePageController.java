package ai.platform.aiassit.knowledge.manage.controller;

import ai.platform.aiassit.service.ai.api.dto.AiKbCreateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentContentUpdateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassit.knowledge.manage.entity.task.dto.AiKbPublishTaskDTO;
import ai.platform.aiassit.knowledge.manage.req.AiKbDeleteRequest;
import ai.platform.aiassit.knowledge.manage.req.AiKbDocumentStatusUpdateRequest;
import ai.platform.aiassit.knowledge.manage.req.AiKbPageDocumentListRequest;
import ai.platform.aiassit.knowledge.manage.req.AiKbSyncCheckRequest;
import ai.platform.aiassit.knowledge.manage.req.AiKbSyncRequest;
import ai.platform.aiassit.knowledge.manage.resp.AiKbDeleteResponse;
import ai.platform.aiassit.knowledge.manage.resp.AiKbSyncCheckResponse;
import ai.platform.aiassit.knowledge.manage.resp.AiKbSyncResponse;
import ai.platform.aiassit.knowledge.manage.domainservice.AiKnowledgeManageDomainService;
import org.athena.framework.data.jdbc.vo.PageResultVO;
import org.athena.framework.web.vo.R;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库管理页面的知识库与文档操作接口。
 *
 * <p>管理面先维护本地知识库和文档，再通过异步同步任务将内容推送到提供方；页面应以同步检查和任务状态判断远端可用性。</p>
 */
@RestController
public class AiKnowledgeBasePageController {

    private final AiKnowledgeManageDomainService domainService;

    public AiKnowledgeBasePageController(AiKnowledgeManageDomainService domainService) {
        this.domainService = domainService;
    }

    /**
     * 查询知识库管理页可见的知识库列表。
     *
     * @return 本地知识库的编码、名称、启用状态和提供方绑定摘要
     */
    @PostMapping(value = "/api/v1/ai/kb/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AiKbInfoDTO> kbList() {
        return domainService.kbList(null);
    }

    /**
     * 创建一个本地知识库及其初始管理配置。
     *
     * @param request 创建请求体，包含知识库名称、编码及基础业务配置
     * @return 新建后的知识库信息
     */
    @PostMapping(value = "/api/v1/ai/kb/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public R<AiKbInfoDTO> createKnowledgeBase(@RequestBody AiKbCreateRequest request) {
        return R.ok(domainService.createKnowledgeBase(request));
    }

    /**
     * 分页查询知识库中的文档。
     *
     * @param request 可选页面查询请求体，包含知识库、关键字、业务类型、标签和分页条件
     * @return 文档分页结果，返回列表展示所需的文档摘要和状态
     */
    @PostMapping(value = "/api/v1/ai/kb/document/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResultVO<AiKbDocumentListItemDTO> listDocuments(@RequestBody(required = false) AiKbPageDocumentListRequest request) {
        AiKbDocumentListRequest payload = new AiKbDocumentListRequest();
        if (request != null) {
            payload.setKbCode(request.getKbCode());
            payload.setKeyword(request.getKeyword());
            payload.setBizTypeCode(request.getBizTypeCode());
            payload.setTab(request.getTab());
            payload.setPage(request.getPage());
            payload.setSize(request.getSize());
        }
        return domainService.listDocuments(payload);
    }

    /**
     * 查询一篇知识库文档的完整内容和元数据。
     *
     * @param kbCode       本地知识库编码
     * @param documentCode 文档业务编码
     * @return 文档详情，包含正文、业务属性和同步状态
     */
    @GetMapping(value = "/api/v1/ai/kb/document/detail", produces = MediaType.APPLICATION_JSON_VALUE)
    public AiKbDocumentDetailDTO getDocumentDetail(@RequestParam("kbCode") String kbCode,
                                                   @RequestParam("documentCode") String documentCode) {
        return domainService.getDocumentDetail(kbCode, documentCode);
    }

    /**
     * 新增或更新本地知识库文档。
     *
     * @param request 文档保存请求体，包含知识库、稳定文档编码、标题、正文和业务类型
     * @return 保存结果，包含文档编码和处理状态
     */
    @PostMapping("/api/v1/ai/kb/document/upsert")
    public R<AiKbDocumentUpsertResponse> upsertDocument(@RequestBody AiKbDocumentUpsertRequest request) {
        return R.ok(domainService.upsertDocument(request));
    }

    /**
     * 仅更新本地知识库文档的正文内容。
     *
     * @param request 正文更新请求体，包含本地文档定位信息和新的内容
     * @return 更新结果，包含文档处理状态
     */
    @PostMapping("/api/v1/ai/kb/document/content/update")
    public R<AiKbDocumentUpsertResponse> updateDocumentContent(@RequestBody AiKbDocumentContentUpdateRequest request) {
        return R.ok(domainService.updateDocumentContent(request));
    }

    /**
     * 将本地文档提交到知识库提供方进行同步。
     *
     * @param request 同步请求体，包含需要同步的知识库和文档范围
     * @return 同步提交或执行结果，包含任务与文档处理状态
     */
    @PostMapping("/api/v1/ai/kb/document/sync")
    public R<AiKbSyncResponse> syncDocument(@RequestBody AiKbSyncRequest request) {
        return R.ok(domainService.syncDocument(request));
    }

    /**
     * 查询知识库文档同步任务的进度与结果。
     *
     * @param taskCode 同步任务编码
     * @return 同步任务详情，包含执行状态、时间和失败信息
     */
    @GetMapping(value = "/api/v1/ai/kb/document/sync/task", produces = MediaType.APPLICATION_JSON_VALUE)
    public R<AiKbPublishTaskDTO> getSyncTask(@RequestParam("taskCode") String taskCode) {
        return R.ok(domainService.getSyncTask(taskCode));
    }

    /**
     * 检查文档在本地和提供方之间的同步状态。
     *
     * @param request 可选检查请求体，用于指定知识库、文档或检查范围
     * @return 同步检查结果，说明可用性、待处理项和异常原因
     */
    @PostMapping("/api/v1/ai/kb/document/sync/check")
    public R<AiKbSyncCheckResponse> checkDocumentSync(@RequestBody(required = false) AiKbSyncCheckRequest request) {
        return R.ok(domainService.checkDocumentSync(request));
    }

    /**
     * 更新知识库文档的业务可用状态。
     *
     * @param request 状态更新请求体，包含文档定位信息和目标状态
     * @return 实际更新的文档数量
     */
    @PostMapping("/api/v1/ai/kb/document/status/update")
    public R<Integer> updateDocumentStatus(@RequestBody AiKbDocumentStatusUpdateRequest request) {
        return R.ok(domainService.updateDocumentStatus(request));
    }

    /**
     * 删除本地知识库文档，并清理其提供方侧已同步内容。
     *
     * @param request 删除请求体，包含知识库和待删除文档编码集合
     * @return 删除结果，包含成功数量和未删除文档编码
     */
    @PostMapping("/api/v1/ai/kb/document/delete")
    public R<AiKbDeleteResponse> deleteDocument(@RequestBody AiKbDeleteRequest request) {
        return R.ok(domainService.deleteDocument(request));
    }
}
