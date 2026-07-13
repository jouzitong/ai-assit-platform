package ai.platform.aiassit.model.controller;

import ai.platform.aiassit.execution.service.AiClientModelDiscoveryService;
import ai.platform.aiassit.model.entity.dto.AiClientConfigDTO;
import ai.platform.aiassit.model.entity.vo.AiClientConfigVO;
import ai.platform.aiassit.model.service.AiClientConfigService;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderModel;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/meta/internal/client-manage")
public class AiClientConfigController {
    private final AiClientConfigService clientConfigService;
    private final AiClientModelDiscoveryService discoveryService;

    public AiClientConfigController(AiClientConfigService clientConfigService, AiClientModelDiscoveryService discoveryService) {
        this.clientConfigService = clientConfigService;
        this.discoveryService = discoveryService;
    }

    @GetMapping
    public List<AiClientConfigVO> list() { return clientConfigService.list(); }

    @PostMapping
    public AiClientConfigVO add(@RequestBody AiClientConfigDTO dto) { return clientConfigService.add(dto); }

    @PutMapping("/{id}")
    public AiClientConfigVO update(@PathVariable Long id, @RequestBody AiClientConfigDTO dto) { return clientConfigService.update(id, dto); }

    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Long id) { return clientConfigService.delete(id); }

    @PostMapping("/{id}/_models")
    public List<ProviderModel> listModels(@PathVariable Long id) { return discoveryService.listModels(id); }
}
