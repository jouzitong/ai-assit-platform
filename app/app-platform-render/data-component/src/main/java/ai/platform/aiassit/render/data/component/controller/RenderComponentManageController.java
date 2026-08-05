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

/**
 * 渲染组件定义、分类和发布状态管理接口。
 *
 * <p>用于维护组件在组件目录中的可见性与运行时配置，页面和 AI 生成流程应只使用已启用且符合目录条件的组件。</p>
 */
@RestController
@RequestMapping("/api/v1/render/components")
public class RenderComponentManageController {

    private final RenderComponentManageService service;

    public RenderComponentManageController(RenderComponentManageService service) {
        this.service = service;
    }

    /**
     * 按条件分页查询渲染组件。
     *
     * @param request 可选查询请求体，包含类别、关键字、状态和分页条件
     * @return 组件管理视图的分页结果
     */
    @PostMapping("/page")
    public PageResultVO<RenderComponentManageVO> page(@RequestBody(required = false) RenderComponentManageQueryRequest request) {
        return service.page(request);
    }

    /**
     * 查询组件目录的统计摘要。
     *
     * @return 组件数量、分类与状态分布等管理统计信息
     */
    @GetMapping("/summary")
    public RenderComponentManageSummaryVO summary() {
        return service.summary();
    }

    /**
     * 查询组件可选择的分类目录。
     *
     * @return 组件分类列表，供组件编辑和筛选使用
     */
    @GetMapping("/categories")
    public List<RenderComponentCategoryVO> categories() {
        return service.categories();
    }

    /**
     * 查询单个组件的完整管理配置。
     *
     * @param id 组件主键
     * @return 组件定义、属性约束、分类和启用状态
     */
    @GetMapping("/{id}")
    public RenderComponentManageVO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    /**
     * 新增一个渲染组件定义。
     *
     * @param request 组件保存请求体，包含组件标识、分类、属性和运行时配置
     * @return 新建后的组件管理视图
     */
    @PostMapping
    public RenderComponentManageVO add(@RequestBody RenderComponentManageRequest request) {
        return service.add(request);
    }

    /**
     * 更新一个渲染组件的完整配置。
     *
     * @param id      组件主键
     * @param request 组件保存请求体，包含新的元信息、属性和运行时配置
     * @return 更新后的组件管理视图
     */
    @PutMapping("/{id}")
    public RenderComponentManageVO update(@PathVariable("id") Long id, @RequestBody RenderComponentManageRequest request) {
        return service.update(id, request);
    }

    /**
     * 修改组件的启用或发布状态。
     *
     * @param id      组件主键
     * @param request 状态更新请求体，包含目标状态和可选变更说明
     * @return 更新后的组件管理视图
     */
    @PutMapping("/{id}/status")
    public RenderComponentManageVO updateStatus(@PathVariable("id") Long id,
                                                @RequestBody RenderComponentStatusUpdateRequest request) {
        return service.updateStatus(id, request);
    }

    /**
     * 删除一个未被业务约束阻止的组件定义。
     *
     * @param id 组件主键
     * @return 是否成功删除
     */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable("id") Long id) {
        return service.delete(id);
    }
}
