package ai.platform.aiassit.render.data.component.controller;

import ai.platform.aiassit.render.data.component.entity.req.RenderComponentManageQueryRequest;
import ai.platform.aiassit.render.data.component.entity.req.RenderComponentManageRequest;
import ai.platform.aiassit.render.data.component.entity.req.RenderComponentStatusUpdateRequest;
import ai.platform.aiassit.render.data.component.entity.vo.RenderComponentCategoryVO;
import ai.platform.aiassit.render.data.component.entity.vo.RenderComponentManageVO;
import ai.platform.aiassit.render.data.component.entity.vo.RenderComponentManageSummaryVO;
import ai.platform.aiassit.render.data.component.service.RenderComponentManageService;
import org.athena.framework.data.jdbc.vo.PageResultVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/render/components")
public class RenderComponentManageController {

    private final RenderComponentManageService service;

    public RenderComponentManageController(RenderComponentManageService service) {
        this.service = service;
    }

    @PostMapping("/page")
    public PageResultVO<RenderComponentManageVO> page(@RequestBody(required = false) RenderComponentManageQueryRequest request) {
        return service.page(request);
    }

    @GetMapping("/summary")
    public RenderComponentManageSummaryVO summary() {
        return service.summary();
    }

    @GetMapping("/categories")
    public List<RenderComponentCategoryVO> categories() {
        return service.categories();
    }

    @GetMapping("/{id}")
    public RenderComponentManageVO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PostMapping
    public RenderComponentManageVO add(@RequestBody RenderComponentManageRequest request) {
        return service.add(request);
    }

    @PutMapping("/{id}")
    public RenderComponentManageVO update(@PathVariable("id") Long id, @RequestBody RenderComponentManageRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/status")
    public RenderComponentManageVO updateStatus(@PathVariable("id") Long id,
                                                @RequestBody RenderComponentStatusUpdateRequest request) {
        return service.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable("id") Long id) {
        return service.delete(id);
    }
}
