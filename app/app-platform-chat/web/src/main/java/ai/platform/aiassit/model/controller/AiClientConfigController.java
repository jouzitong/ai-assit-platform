package ai.platform.aiassit.model.controller;

import ai.platform.aiassit.execution.service.AiClientModelDiscoveryService;
import ai.platform.aiassit.model.entity.dto.AiClientConfigDTO;
import ai.platform.aiassit.model.entity.vo.AiClientConfigVO;
import ai.platform.aiassit.model.service.AiClientConfigService;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderModel;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 提供方客户端连接配置管理接口。
 *
 * <p>维护各模型提供方的接入配置，并可按指定客户端发现其实际可用模型；凭据等敏感字段由服务层安全处理。</p>
 */
@RestController
@RequestMapping("/api/v1/ai/meta/internal/client-manage")
public class AiClientConfigController {
    private final AiClientConfigService clientConfigService;
    private final AiClientModelDiscoveryService discoveryService;

    public AiClientConfigController(AiClientConfigService clientConfigService, AiClientModelDiscoveryService discoveryService) {
        this.clientConfigService = clientConfigService;
        this.discoveryService = discoveryService;
    }

    /**
     * 查询全部 AI 客户端连接配置。
     *
     * @return 客户端配置视图列表，不返回敏感凭据明文
     */
    @GetMapping
    public List<AiClientConfigVO> list() { return clientConfigService.list(); }

    /**
     * 新增一个 AI 提供方客户端连接配置。
     *
     * @param dto 客户端保存请求体，包含提供方类型、地址、认证和默认参数
     * @return 新建后的客户端配置视图
     */
    @PostMapping
    public AiClientConfigVO add(@RequestBody AiClientConfigDTO dto) { return clientConfigService.add(dto); }

    /**
     * 更新指定 AI 客户端连接配置。
     *
     * @param id  客户端配置主键
     * @param dto 保存请求体，包含需要更新的接入参数
     * @return 更新后的客户端配置视图
     */
    @PutMapping("/{id}")
    public AiClientConfigVO update(@PathVariable Long id, @RequestBody AiClientConfigDTO dto) { return clientConfigService.update(id, dto); }

    /**
     * 删除一个 AI 客户端连接配置。
     *
     * @param id 客户端配置主键
     * @return 是否成功删除
     */
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Long id) { return clientConfigService.delete(id); }

    /**
     * 向指定客户端实时发现其可用模型。
     *
     * @param id 客户端配置主键
     * @return 提供方返回的模型目录，用于后续模型配置选择
     */
    @PostMapping("/{id}/_models")
    public List<ProviderModel> listModels(@PathVariable Long id) { return discoveryService.listModels(id); }
}
