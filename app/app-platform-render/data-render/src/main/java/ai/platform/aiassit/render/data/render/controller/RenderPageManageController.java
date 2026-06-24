package ai.platform.aiassit.render.data.render.controller;

import ai.platform.aiassit.render.data.render.entity.req.RenderPageManageQueryRequest;
import ai.platform.aiassit.render.data.render.entity.req.RenderPageManageRequest;
import ai.platform.aiassit.render.data.render.entity.req.RenderPageTreeQueryRequest;
import ai.platform.aiassit.render.data.render.entity.vo.RenderPageManageVO;
import ai.platform.aiassit.render.data.render.entity.vo.RenderPageTreeVO;
import ai.platform.aiassit.render.data.render.service.RenderPageManageService;
import org.athena.framework.data.jdbc.vo.PageResultVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/render/pages")
public class RenderPageManageController {

    private final RenderPageManageService service;

    public RenderPageManageController(RenderPageManageService service) {
        this.service = service;
    }

    @PostMapping("/page")
    public PageResultVO<RenderPageManageVO> page(@RequestBody(required = false) RenderPageManageQueryRequest request) {
        return service.page(request);
    }

    @PostMapping("/tree")
    public RenderPageTreeVO tree(@RequestBody(required = false) RenderPageTreeQueryRequest request) {
        return service.tree(request);
    }

    @GetMapping("/{id}")
    public RenderPageManageVO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PostMapping
    public RenderPageManageVO add(@RequestBody RenderPageManageRequest request) {
        return service.add(request);
    }

    @PutMapping("/{id}")
    public RenderPageManageVO update(@PathVariable("id") Long id, @RequestBody RenderPageManageRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable("id") Long id) {
        return service.delete(id);
    }
}
