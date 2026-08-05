package ai.platform.aiassit.knowledge.manage.controller;

import ai.platform.aiassit.execution.service.KnowledgeClientConfigService;
import ai.platform.aiassit.execution.service.KnowledgeClientOption;
import ai.platform.aiassit.knowledge.manage.domainservice.AiKnowledgeDatasetService;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetSaveRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbEmbeddingModelDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbEmbeddingModelListRequest;
import org.athena.framework.web.vo.R;
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
 * 知识库管理页的系统客户端选择和远端 Dataset 管理接口。
 *
 * <p>通过已配置的知识库客户端访问提供方侧资源；认证信息始终停留在服务端，不会随接口响应返回给页面。</p>
 */
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

    /**
     * 查询当前系统可选择的知识库客户端。
     *
     * @return 客户端选项列表，包含可展示标识、提供方类型和可用状态，不包含凭据
     */
    @GetMapping
    public R<List<KnowledgeClientOption>> listOptions() {
        return R.ok(knowledgeClientConfigService.listOptions());
    }

    /**
     * 查询指定客户端在提供方侧可见的 Dataset。
     *
     * @param clientKey 系统知识库客户端标识
     * @param request   可选查询请求体，用于提供方侧筛选或分页
     * @return 远端 Dataset 列表，供本地知识库绑定或管理使用
     */
    @PostMapping("/{clientKey}/datasets")
    public R<List<AiKbDatasetDTO>> listDatasets(@PathVariable String clientKey,
                                                @RequestBody(required = false) AiKbDatasetListRequest request) {
        return R.ok(datasetService.listDatasets(clientKey, request));
    }

    /**
     * 查询指定客户端可用于 Dataset 的 Embedding 模型。
     *
     * @param clientKey 系统知识库客户端标识
     * @param request   可选查询请求体，用于筛选可用模型
     * @return 提供方支持的向量模型列表
     */
    @PostMapping("/{clientKey}/embedding-models")
    public R<List<AiKbEmbeddingModelDTO>> listEmbeddingModels(@PathVariable String clientKey,
                                                              @RequestBody(required = false) AiKbEmbeddingModelListRequest request) {
        return R.ok(datasetService.listEmbeddingModels(clientKey, request));
    }

    /**
     * 通过已选系统客户端创建提供方侧 Dataset。
     *
     * @param clientKey 系统知识库客户端标识
     * @param request   Dataset 保存请求体，包含名称、描述和向量化配置
     * @return 新建的远端 Dataset 信息，不包含认证信息
     */
    @PostMapping("/{clientKey}/datasets/create")
    public R<AiKbDatasetDTO> createDataset(@PathVariable String clientKey,
                                           @RequestBody AiKbDatasetSaveRequest request) {
        return R.ok(datasetService.createDataset(clientKey, request));
    }

    /**
     * 更新提供方侧已有 Dataset 的可修改配置。
     *
     * @param clientKey 系统知识库客户端标识
     * @param kbId      提供方 Dataset 标识
     * @param request   Dataset 保存请求体，包含要更新的配置
     * @return 更新后的远端 Dataset 信息
     */
    @PutMapping("/{clientKey}/datasets/{kbId}")
    public R<AiKbDatasetDTO> updateDataset(@PathVariable String clientKey,
                                           @PathVariable String kbId,
                                           @RequestBody AiKbDatasetSaveRequest request) {
        return R.ok(datasetService.updateDataset(clientKey, kbId, request));
    }

    /**
     * 通过已选系统客户端删除提供方侧一个或多个 Dataset。
     *
     * @param clientKey 系统知识库客户端标识
     * @param request   删除请求体，包含待删除的 Dataset 标识集合
     * @return 提供方实际删除的 Dataset 数量
     */
    @DeleteMapping("/{clientKey}/datasets")
    public R<Integer> deleteDatasets(@PathVariable String clientKey,
                                     @RequestBody AiKbDatasetDeleteRequest request) {
        return R.ok(datasetService.deleteDatasets(clientKey, request));
    }
}
