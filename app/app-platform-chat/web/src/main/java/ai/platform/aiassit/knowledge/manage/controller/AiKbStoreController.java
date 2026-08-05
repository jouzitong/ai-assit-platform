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

/**
 * 本地知识库目录与提供方同步状态管理接口。
 *
 * <p>维护平台知识库编码与提供方 Dataset 的绑定、启用状态及同步状态，供文档管理和运行时检索使用。</p>
 */
@RestController
@RequestMapping("/api/v1/ai/kb/internal/store")
public class AiKbStoreController {

    private final AiKbStoreManageDomainService domainService;

    public AiKbStoreController(AiKbStoreManageDomainService domainService) {
        this.domainService = domainService;
    }

    /**
     * 按条件分页查询本地知识库目录。
     *
     * @param request 可选查询请求体，包含编码、名称、客户端或同步状态等筛选条件
     * @return 知识库目录分页结果，包含本地配置与同步摘要
     */
    @PostMapping("/_search")
    public PageResultVO<AiKbStoreVO> page(@RequestBody(required = false) AiKbStoreQueryRequest request) {
        return domainService.page(request);
    }

    /**
     * 查询一条本地知识库配置详情。
     *
     * @param id 本地知识库记录主键
     * @return 知识库配置、绑定 Dataset 和同步状态
     */
    @GetMapping("/{id}")
    public AiKbStoreVO get(@PathVariable Long id) {
        return domainService.get(id);
    }

    /**
     * 新建本地知识库配置并建立提供方绑定信息。
     *
     * @param dto 知识库保存请求体，包含本地编码、客户端选择、Dataset 及启用配置
     * @return 新建后的知识库详情
     */
    @PostMapping
    public AiKbStoreVO add(@RequestBody AiKbStoreDTO dto) {
        return domainService.add(dto);
    }

    /**
     * 全量更新本地知识库配置。
     *
     * @param id  本地知识库记录主键
     * @param dto 保存请求体，包含需要替换的配置与绑定信息
     * @return 更新后的知识库详情
     */
    @PutMapping("/{id}")
    public AiKbStoreVO update(@PathVariable Long id, @RequestBody AiKbStoreDTO dto) {
        return domainService.update(id, dto);
    }

    /**
     * 局部更新本地知识库配置。
     *
     * @param id  本地知识库记录主键
     * @param dto 保存请求体，仅变更传入的可编辑字段
     * @return 更新后的知识库详情
     */
    @PatchMapping("/{id}")
    public AiKbStoreVO edit(@PathVariable Long id, @RequestBody AiKbStoreDTO dto) {
        return domainService.edit(id, dto);
    }

    /**
     * 删除本地知识库配置。
     *
     * @param id 本地知识库记录主键
     * @return 是否成功删除本地目录记录
     */
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Long id) {
        return domainService.delete(id);
    }

    /**
     * 重试指定知识库最近失败或未完成的提供方同步。
     *
     * @param id 本地知识库记录主键
     * @return 是否已成功触发或完成重试同步
     */
    @PostMapping("/{id}/_retry-sync")
    public Boolean retrySync(@PathVariable Long id) {
        return domainService.retrySync(id);
    }
}
