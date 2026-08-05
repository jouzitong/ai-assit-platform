package ai.platform.aiassit.model.controller;

import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.model.entity.req.AiMetaQueryRequest;
import ai.platform.aiassit.model.service.AiModelConfigService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 模型基础配置的通用 CRUD 接口。
 *
 * <p>复用 {@link BaseController} 提供的新增、详情、分页、更新和删除能力；请求体使用 {@link AiModelConfigDTO}，查询条件使用
 * {@link AiMetaQueryRequest}，响应返回模型配置及其审计信息。</p>
 */
@RestController
@RequestMapping("/api/v1/ai/meta/internal/model")
public class AiModelConfigController
        extends BaseController<AiModelConfigDTO, AiMetaQueryRequest, AiModelConfigService> {

    private final AiModelConfigService service;

    public AiModelConfigController(AiModelConfigService service) {
        this.service = service;
    }

    @Override
    protected AiModelConfigService service() {
        return service;
    }
}
