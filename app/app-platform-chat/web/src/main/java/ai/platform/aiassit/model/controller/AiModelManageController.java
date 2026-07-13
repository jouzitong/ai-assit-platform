package ai.platform.aiassit.model.controller;

import ai.platform.aiassit.execution.dto.AiModelTestChatRequestDTO;
import ai.platform.aiassit.execution.dto.AiModelTestChatResultVO;
import ai.platform.aiassit.execution.service.AiModelTestService;
import ai.platform.aiassit.model.domainservice.AiModelManageDomainService;
import ai.platform.aiassit.model.entity.dto.AiModelManageDTO;
import ai.platform.aiassit.model.entity.dto.AiModelBatchSaveDTO;
import ai.platform.aiassit.model.entity.req.AiModelManageQueryRequest;
import ai.platform.aiassit.model.entity.vo.AiModelManageVO;
import org.athena.framework.data.jdbc.vo.PageResultVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/meta/internal/model-manage")
public class AiModelManageController {

    private final AiModelManageDomainService domainService;
    private final AiModelTestService modelTestService;

    public AiModelManageController(AiModelManageDomainService domainService,
                                   AiModelTestService modelTestService) {
        this.domainService = domainService;
        this.modelTestService = modelTestService;
    }

    @PostMapping("/_search")
    public PageResultVO<AiModelManageVO> page(@RequestBody(required = false) AiModelManageQueryRequest query) {
        return domainService.page(query);
    }

    @GetMapping("/{id}")
    public AiModelManageVO get(@PathVariable("id") Long id) {
        return domainService.get(id);
    }

    @PostMapping
    public AiModelManageVO add(@RequestBody AiModelManageDTO dto) {
        return domainService.add(dto);
    }

    @PostMapping("/_batch")
    public java.util.List<AiModelManageVO> batchSave(@RequestBody AiModelBatchSaveDTO dto) {
        return domainService.batchSave(dto);
    }

    @PostMapping("/_test-chat")
    public AiModelTestChatResultVO testChat(@RequestBody AiModelTestChatRequestDTO request) {
        return modelTestService.testChat(request);
    }

    @PutMapping("/{id}")
    public AiModelManageVO update(@PathVariable("id") Long id, @RequestBody AiModelManageDTO dto) {
        return domainService.update(id, dto);
    }

    @PatchMapping("/{id}")
    public AiModelManageVO edit(@PathVariable("id") Long id, @RequestBody AiModelManageDTO dto) {
        return domainService.edit(id, dto);
    }

    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable("id") Long id) {
        return domainService.delete(id);
    }
}
