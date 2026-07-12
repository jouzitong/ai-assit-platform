package ai.platform.aiassit.knowledge.manage.controller;

import ai.platform.aiassit.knowledge.manage.domainservice.AiKbStoreManageDomainService;
import ai.platform.aiassit.knowledge.manage.entity.store.dto.AiKbStoreDTO;
import ai.platform.aiassit.knowledge.manage.entity.store.req.AiKbStoreQueryRequest;
import ai.platform.aiassit.knowledge.manage.vo.AiKbStoreVO;
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
@RequestMapping("/api/v1/ai/kb/internal/store")
public class AiKbStoreController {

    private final AiKbStoreManageDomainService domainService;

    public AiKbStoreController(AiKbStoreManageDomainService domainService) {
        this.domainService = domainService;
    }

    @PostMapping("/_search")
    public PageResultVO<AiKbStoreVO> page(@RequestBody(required = false) AiKbStoreQueryRequest request) {
        return domainService.page(request);
    }

    @GetMapping("/{id}")
    public AiKbStoreVO get(@PathVariable Long id) {
        return domainService.get(id);
    }

    @PostMapping
    public AiKbStoreVO add(@RequestBody AiKbStoreDTO dto) {
        return domainService.add(dto);
    }

    @PutMapping("/{id}")
    public AiKbStoreVO update(@PathVariable Long id, @RequestBody AiKbStoreDTO dto) {
        return domainService.update(id, dto);
    }

    @PatchMapping("/{id}")
    public AiKbStoreVO edit(@PathVariable Long id, @RequestBody AiKbStoreDTO dto) {
        return domainService.edit(id, dto);
    }

    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Long id) {
        return domainService.delete(id);
    }
}
