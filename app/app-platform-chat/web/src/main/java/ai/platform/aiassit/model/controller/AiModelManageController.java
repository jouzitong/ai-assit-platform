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

/**
 * 面向运行时的 AI 模型管理接口。
 *
 * <p>在基础模型配置之外，提供模型分页维护、批量保存与连通性试聊能力，确保业务模型可映射到已配置的提供方客户端。</p>
 */
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

    /**
     * 按条件分页查询可管理的 AI 模型。
     *
     * @param query 可选查询请求体，包含模型、客户端、状态等筛选条件
     * @return 模型管理视图的分页结果
     */
    @PostMapping("/_search")
    public PageResultVO<AiModelManageVO> page(@RequestBody(required = false) AiModelManageQueryRequest query) {
        return domainService.page(query);
    }

    /**
     * 查询单个模型的管理配置详情。
     *
     * @param id 模型配置主键
     * @return 模型、客户端映射和运行参数详情
     */
    @GetMapping("/{id}")
    public AiModelManageVO get(@PathVariable("id") Long id) {
        return domainService.get(id);
    }

    /**
     * 新增一个可供业务选择的 AI 模型配置。
     *
     * @param dto 模型保存请求体，包含模型标识、客户端绑定和运行参数
     * @return 新建后的模型管理视图
     */
    @PostMapping
    public AiModelManageVO add(@RequestBody AiModelManageDTO dto) {
        return domainService.add(dto);
    }

    /**
     * 批量保存模型配置，通常用于同步或一次性调整模型目录。
     *
     * @param dto 批量保存请求体，包含待新增或更新的模型配置集合
     * @return 每个保存成功模型的管理视图列表
     */
    @PostMapping("/_batch")
    public java.util.List<AiModelManageVO> batchSave(@RequestBody AiModelBatchSaveDTO dto) {
        return domainService.batchSave(dto);
    }

    /**
     * 使用指定模型执行一次试聊以验证连通性和生成效果。
     *
     * @param request 试聊请求体，包含目标模型、测试消息和生成参数
     * @return 试聊结果，包含模型响应、耗时和失败诊断
     */
    @PostMapping("/_test-chat")
    public AiModelTestChatResultVO testChat(@RequestBody AiModelTestChatRequestDTO request) {
        return modelTestService.testChat(request);
    }

    /**
     * 全量更新一个模型管理配置。
     *
     * @param id  模型配置主键
     * @param dto 保存请求体，包含替换后的模型和客户端参数
     * @return 更新后的模型管理视图
     */
    @PutMapping("/{id}")
    public AiModelManageVO update(@PathVariable("id") Long id, @RequestBody AiModelManageDTO dto) {
        return domainService.update(id, dto);
    }

    /**
     * 局部更新一个模型管理配置。
     *
     * @param id  模型配置主键
     * @param dto 保存请求体，仅修改传入的可编辑字段
     * @return 更新后的模型管理视图
     */
    @PatchMapping("/{id}")
    public AiModelManageVO edit(@PathVariable("id") Long id, @RequestBody AiModelManageDTO dto) {
        return domainService.edit(id, dto);
    }

    /**
     * 删除一个模型管理配置。
     *
     * @param id 模型配置主键
     * @return 是否成功删除
     */
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable("id") Long id) {
        return domainService.delete(id);
    }
}
