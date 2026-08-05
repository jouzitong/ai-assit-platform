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

/**
 * 渲染页面定义与页面树管理接口。
 *
 * <p>管理页面的元信息、层级关系和可编辑配置；页面运行内容由独立内容接口维护，避免大 JSON 与列表管理耦合。</p>
 */
@RestController
@RequestMapping("/api/v1/render/pages")
public class RenderPageManageController {

    private final RenderPageManageService service;

    public RenderPageManageController(RenderPageManageService service) {
        this.service = service;
    }

    /**
     * 按条件分页查询渲染页面。
     *
     * @param request 可选查询请求体，包含分类、关键字、状态和分页条件
     * @return 页面管理视图的分页结果
     */
    @PostMapping("/page")
    public PageResultVO<RenderPageManageVO> page(@RequestBody(required = false) RenderPageManageQueryRequest request) {
        return service.page(request);
    }

    /**
     * 查询按目录组织的渲染页面树。
     *
     * @param request 可选树查询请求体，包含分类根节点和展示过滤条件
     * @return 带层级关系的页面树，供导航与页面选择使用
     */
    @PostMapping("/tree")
    public RenderPageTreeVO tree(@RequestBody(required = false) RenderPageTreeQueryRequest request) {
        return service.tree(request);
    }

    /**
     * 查询一个渲染页面的管理配置详情。
     *
     * @param id 页面主键
     * @return 页面元信息、分类归属和运行配置摘要
     */
    @GetMapping("/{id}")
    public RenderPageManageVO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    /**
     * 新增一条渲染页面定义。
     *
     * @param request 页面保存请求体，包含页面编码、名称、分类和基础配置
     * @return 新建后的页面管理视图
     */
    @PostMapping
    public RenderPageManageVO add(@RequestBody RenderPageManageRequest request) {
        return service.add(request);
    }

    /**
     * 更新一个渲染页面的管理配置。
     *
     * @param id      页面主键
     * @param request 页面保存请求体，包含要替换的页面元信息和基础配置
     * @return 更新后的页面管理视图
     */
    @PutMapping("/{id}")
    public RenderPageManageVO update(@PathVariable("id") Long id, @RequestBody RenderPageManageRequest request) {
        return service.update(id, request);
    }

    /**
     * 删除一个渲染页面定义。
     *
     * @param id 页面主键
     * @return 是否成功删除
     */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable("id") Long id) {
        return service.delete(id);
    }
}
