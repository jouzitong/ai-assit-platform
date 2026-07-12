package ai.platform.aiassit.knowledge.manage.controller;

import ai.platform.aiassit.service.ai.api.dto.AiKbCreateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentContentUpdateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbPublishTaskDTO;
import ai.platform.aiassit.knowledge.manage.req.AiKbDeleteRequest;
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

@RestController
public class AiKnowledgeBasePageController {

    private final AiKnowledgeManageDomainService domainService;

    public AiKnowledgeBasePageController(AiKnowledgeManageDomainService domainService) {
        this.domainService = domainService;
    }

    @PostMapping(value = "/api/v1/ai/kb/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AiKbInfoDTO> kbList() {
        return domainService.kbList(null);
    }

    @PostMapping(value = "/api/v1/ai/kb/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public R<AiKbInfoDTO> createKnowledgeBase(@RequestBody AiKbCreateRequest request) {
        return R.ok(domainService.createKnowledgeBase(request));
    }

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

    @GetMapping(value = "/api/v1/ai/kb/document/detail", produces = MediaType.APPLICATION_JSON_VALUE)
    public AiKbDocumentDetailDTO getDocumentDetail(@RequestParam("kbCode") String kbCode,
                                                   @RequestParam("documentCode") String documentCode) {
        return domainService.getDocumentDetail(kbCode, documentCode);
    }

    @PostMapping("/api/v1/ai/kb/document/upsert")
    public R<AiKbDocumentUpsertResponse> upsertDocument(@RequestBody AiKbDocumentUpsertRequest request) {
        return R.ok(domainService.upsertDocument(request));
    }

    @PostMapping("/api/v1/ai/kb/document/content/update")
    public R<AiKbDocumentUpsertResponse> updateDocumentContent(@RequestBody AiKbDocumentContentUpdateRequest request) {
        return R.ok(domainService.updateDocumentContent(request));
    }

    @PostMapping("/api/v1/ai/kb/document/sync")
    public R<AiKbSyncResponse> syncDocument(@RequestBody AiKbSyncRequest request) {
        return R.ok(domainService.syncDocument(request));
    }

    @GetMapping(value = "/api/v1/ai/kb/document/sync/task", produces = MediaType.APPLICATION_JSON_VALUE)
    public R<AiKbPublishTaskDTO> getSyncTask(@RequestParam("taskCode") String taskCode) {
        return R.ok(domainService.getSyncTask(taskCode));
    }

    @PostMapping("/api/v1/ai/kb/document/sync/check")
    public R<AiKbSyncCheckResponse> checkDocumentSync(@RequestBody(required = false) AiKbSyncCheckRequest request) {
        return R.ok(domainService.checkDocumentSync(request));
    }

    @PostMapping("/api/v1/ai/kb/document/delete")
    public R<AiKbDeleteResponse> deleteDocument(@RequestBody AiKbDeleteRequest request) {
        return R.ok(domainService.deleteDocument(request));
    }
}
