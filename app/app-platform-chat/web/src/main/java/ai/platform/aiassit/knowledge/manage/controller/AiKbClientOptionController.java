package ai.platform.aiassit.knowledge.manage.controller;

import ai.platform.aiassit.execution.service.KnowledgeClientConfigService;
import ai.platform.aiassit.execution.service.KnowledgeClientOption;
import ai.platform.aiassit.knowledge.manage.domainservice.AiKnowledgeDatasetService;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 知识库管理页使用的系统客户端选择与远端 Dataset 查询接口。 */
@RestController
@RequestMapping("/api/v1/ai/kb/internal/client-options")
public class AiKbClientOptionController {

    private final KnowledgeClientConfigService knowledgeClientConfigService;
    private final AiKnowledgeDatasetService datasetService;

    public AiKbClientOptionController(KnowledgeClientConfigService knowledgeClientConfigService,
                                      AiKnowledgeDatasetService datasetService) {
        this.knowledgeClientConfigService = knowledgeClientConfigService;
        this.datasetService = datasetService;
    }

    @GetMapping
    public R<List<KnowledgeClientOption>> listOptions() {
        return R.ok(knowledgeClientConfigService.listOptions());
    }

    @PostMapping("/{clientKey}/datasets")
    public R<List<AiKbDatasetDTO>> listDatasets(@PathVariable String clientKey,
                                                @RequestBody(required = false) AiKbDatasetListRequest request) {
        return R.ok(datasetService.listDatasets(clientKey, request));
    }
}
