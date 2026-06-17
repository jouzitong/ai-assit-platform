package ai.platform.aiassist.service.ai.api;

import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListRequest;
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
 */
@FeignClient(
        name = "aiEngine",
        contextId = "platformAiKnowledgeBaseManageClient",
        path = "/aiEngine"
)
public interface AiKnowledgeBaseManageApi {

    @PostMapping("/internal/v1/ai/kb/document/upsert")
    R<AiKbDocumentUpsertResponse> upsertDocument(@RequestBody AiKbDocumentUpsertRequest request);

    @PostMapping(value = "/internal/v1/ai/kb/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @IgnoredResultWrapper
    List<AiKbInfoDTO> kbList(@RequestBody(required = false) AiKbListRequest request);

    @PostMapping(value = "/internal/v1/ai/kb/document/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @IgnoredResultWrapper
    List<AiKbDocumentListItemDTO> listDocuments(@RequestBody(required = false) AiKbDocumentListRequest request);

    @GetMapping(value = "/internal/v1/ai/kb/document/detail", produces = MediaType.APPLICATION_JSON_VALUE)
    @IgnoredResultWrapper
    AiKbDocumentDetailDTO getDocumentDetail(@RequestParam("kbCode") String kbCode, @RequestParam("documentCode") String documentCode);

}
