package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.data.virtualization.api.dto.TransformLineageResponse;
import ai.platform.aiassit.data.virtualization.api.dto.TransformPreviewRequest;
import ai.platform.aiassit.data.virtualization.api.dto.TransformPreviewResponse;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.catalog.CreateVirtualEntityFromTableRequest;
import ai.platform.aiassit.data.virtualization.core.catalog.VirtualCatalogPublisher;
import ai.platform.aiassit.data.virtualization.core.catalog.VirtualEntityDraftFactory;
import ai.platform.aiassit.data.virtualization.core.knowledge.VirtualDescriptionGenerateRequest;
import ai.platform.aiassit.data.virtualization.core.knowledge.VirtualDescriptionGenerateResponse;
import ai.platform.aiassit.data.virtualization.core.knowledge.VirtualDescriptionService;
import ai.platform.aiassit.data.virtualization.core.knowledge.VirtualKnowledgeBatchRequest;
import ai.platform.aiassit.data.virtualization.core.knowledge.VirtualKnowledgeConfigurationResponse;
import ai.platform.aiassit.data.virtualization.core.knowledge.VirtualKnowledgePreviewResponse;
import ai.platform.aiassit.data.virtualization.core.knowledge.VirtualKnowledgeService;
import ai.platform.aiassit.data.virtualization.core.knowledge.VirtualKnowledgeStatusItem;
import ai.platform.aiassit.data.virtualization.core.knowledge.VirtualKnowledgeSyncResponse;
import ai.platform.aiassit.data.virtualization.core.knowledge.VirtualUnpublishResponse;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformManagementService;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformerRegistry;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformScriptGenerateRequest;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformScriptGenerateResponse;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformScriptService;
import ai.platform.aiassit.db.engine.api.constant.DbEngineSystemSettingKeys;
import ai.platform.aiassit.db.engine.virtualization.adapter.external.VirtualKnowledgeBaseSettingResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 虚拟数据目录发布、知识库同步与字段转换管理接口。
 *
 * <p>负责把物理表抽象为可审核的虚拟实体、校验并发布目录、维护字段转换规则，以及将已发布的业务语义同步到知识库；不会直接开放物理数据源访问。</p>
 */
@RestController
@RequestMapping("/api/v1/virtual-data")
public class VirtualCatalogManagementController {
    private final VirtualEntityDraftFactory draftFactory;
    private final VirtualCatalogPublisher publisher;
    private final FieldTransformManagementService transformService;
    private final VirtualKnowledgeService knowledgeService;
    private final VirtualKnowledgeBaseSettingResolver knowledgeBaseSettingResolver;
    private final VirtualDescriptionService descriptionService;
    private final FieldTransformScriptService scriptService;

    public VirtualCatalogManagementController(
            VirtualEntityDraftFactory draftFactory,
            VirtualCatalogPublisher publisher,
            FieldTransformManagementService transformService,
            VirtualKnowledgeService knowledgeService,
            VirtualKnowledgeBaseSettingResolver knowledgeBaseSettingResolver,
            VirtualDescriptionService descriptionService,
            FieldTransformScriptService scriptService
    ) {
        this.draftFactory = draftFactory;
        this.publisher = publisher;
        this.transformService = transformService;
        this.knowledgeService = knowledgeService;
        this.knowledgeBaseSettingResolver = knowledgeBaseSettingResolver;
        this.descriptionService = descriptionService;
        this.scriptService = scriptService;
    }

    /**
     * 根据物理表元数据创建一份虚拟实体草稿。
     *
     * @param request 创建请求体，包含物理表定位、目标实体语义和字段映射选项
     * @return 新生成的目录快照；该操作只创建草稿，不会自动发布
     */
    @PostMapping("/entities/from-physical-table")
    public CatalogSnapshot createFromPhysicalTable(@RequestBody CreateVirtualEntityFromTableRequest request) {
        return draftFactory.create(request);
    }

    /**
     * 校验并发布一个虚拟实体到可执行目录。
     *
     * @param entityId 待发布虚拟实体的主键
     * @return 发布后的目录快照，包含解析完成的实体、字段和绑定信息
     */
    @PostMapping("/publish")
    public CatalogSnapshot publish(@RequestParam Long entityId) {
        return publisher.publish(entityId);
    }

    /**
     * 批量校验并发布多个虚拟实体。
     *
     * @param request 批量请求体，包含待发布的虚拟实体主键集合
     * @return 每个实体发布后的目录快照列表，重复标识会被去重
     */
    @PostMapping("/publish-batch")
    public List<CatalogSnapshot> publishBatch(@RequestBody VirtualKnowledgeBatchRequest request) {
        return entityIds(request).stream().distinct().map(publisher::publish).toList();
    }

    /**
     * 为虚拟实体生成面向业务使用者的描述草稿。
     *
     * @param request 描述生成请求体，包含实体、字段上下文和生成偏好
     * @return 描述生成结果，供开发者审核后保存或同步
     */
    @PostMapping("/entities/description/generate")
    public VirtualDescriptionGenerateResponse generateDescription(@RequestBody VirtualDescriptionGenerateRequest request) {
        return descriptionService.generate(request);
    }

    /**
     * 预览某个虚拟实体同步到知识库前的知识文本。
     *
     * @param entityId 虚拟实体主键
     * @return 知识库同步预览，包含将写入的语义描述和关联元数据
     */
    @GetMapping("/knowledge-preview")
    public VirtualKnowledgePreviewResponse knowledgePreview(@RequestParam Long entityId) {
        return knowledgeService.preview(entityId);
    }

    /**
     * 查询多个虚拟实体的知识库同步状态。
     *
     * @param request 批量请求体，包含待检查的虚拟实体主键集合
     * @return 每个实体的知识库可用性、同步时间和异常状态
     */
    @PostMapping("/knowledge-status")
    public List<VirtualKnowledgeStatusItem> knowledgeStatus(@RequestBody VirtualKnowledgeBatchRequest request) {
        return knowledgeService.status(entityIds(request));
    }

    /**
     * 查询虚拟目录知识同步使用的知识库配置。
     *
     * @return 配置键及当前解析出的知识库业务编码，不返回认证信息
     */
    @GetMapping("/knowledge-configuration")
    public VirtualKnowledgeConfigurationResponse knowledgeConfiguration() {
        return new VirtualKnowledgeConfigurationResponse(
                DbEngineSystemSettingKeys.KNOWLEDGE_BASE_CODE,
                knowledgeBaseSettingResolver.resolve()
        );
    }

    /**
     * 将指定虚拟实体初始化并同步到配置的知识库。
     *
     * @param request 批量请求体，包含待同步的虚拟实体主键集合
     * @return 同步结果，包含各实体写入或更新状态
     */
    @PostMapping("/knowledge-sync")
    public VirtualKnowledgeSyncResponse knowledgeSync(@RequestBody VirtualKnowledgeBatchRequest request) {
        return knowledgeService.initialize(knowledgeBaseSettingResolver.resolve(), entityIds(request));
    }

    /**
     * 兼容入口：初始化并同步指定虚拟实体的知识库内容。
     *
     * @param request 批量请求体，包含待初始化的虚拟实体主键集合
     * @return 初始化同步结果，包含各实体的处理状态
     */
    @PostMapping("/knowledge-initialize")
    public VirtualKnowledgeSyncResponse knowledgeInitialize(@RequestBody VirtualKnowledgeBatchRequest request) {
        return knowledgeService.initialize(knowledgeBaseSettingResolver.resolve(), entityIds(request));
    }

    /**
     * 在取消发布前检查虚拟实体的知识库依赖和同步状态。
     *
     * @param request 批量请求体，包含待取消发布的虚拟实体主键集合
     * @return 每个实体的依赖状态，供调用方确认取消发布影响
     */
    @PostMapping("/unpublish-check")
    public List<VirtualKnowledgeStatusItem> unpublishCheck(@RequestBody VirtualKnowledgeBatchRequest request) {
        return knowledgeService.status(entityIds(request));
    }

    /**
     * 取消发布虚拟实体并处理关联的知识库可用性。
     *
     * @param request 批量请求体，包含待取消发布的虚拟实体主键集合
     * @return 取消发布结果，包含实体和知识库清理状态
     */
    @PostMapping("/unpublish")
    public VirtualUnpublishResponse unpublish(@RequestBody VirtualKnowledgeBatchRequest request) {
        return knowledgeService.unpublish(entityIds(request));
    }

    /**
     * 校验虚拟实体目录是否满足发布条件。
     *
     * @param entityId 待校验虚拟实体的主键
     * <p>成功时仅返回 HTTP 成功状态；失败时返回目录、绑定、字段或关系的校验错误。</p>
     */
    @PostMapping("/validate")
    public void validateCatalog(@RequestParam Long entityId) {
        publisher.validate(entityId);
    }

    /**
     * 查询平台已注册的字段转换器能力。
     *
     * @return 转换器描述列表，包含名称、参数契约和适用字段类型
     */
    @GetMapping("/field-transformers")
    public List<FieldTransformerRegistry.Descriptor> transformers() {
        return transformService.transformers();
    }

    /**
     * 校验一条字段转换规则的配置和可执行性。
     *
     * @param ruleId 待校验字段转换规则的主键
     * <p>成功时仅返回 HTTP 成功状态；失败时返回规则参数、脚本或引用端口的校验错误。</p>
     */
    @PostMapping("/field-transform-rules/validate")
    public void validateRule(@RequestParam Long ruleId) {
        transformService.validate(ruleId);
    }

    /**
     * 根据字段语义和转换目标生成规则脚本草稿。
     *
     * @param request 脚本生成请求体，包含源字段、目标字段和转换要求
     * @return 脚本生成结果，供开发者审核后保存到转换规则
     */
    @PostMapping("/field-transform-rules/script/generate")
    public FieldTransformScriptGenerateResponse generateFieldTransformScript(
            @RequestBody FieldTransformScriptGenerateRequest request
    ) {
        return scriptService.generate(request);
    }

    /**
     * 使用样例输入预览字段转换规则的效果。
     *
     * @param request 预览请求体，包含规则定位、样例数据和运行选项
     * @return 转换预览结果，包含输出值、处理步骤和错误信息
     */
    @PostMapping("/field-transform-rules/preview")
    public TransformPreviewResponse preview(@RequestBody TransformPreviewRequest request) {
        return transformService.preview(request);
    }

    /**
     * 查询虚拟字段与物理字段之间的转换血缘关系。
     *
     * @param virtualFieldId      可选虚拟字段主键
     * @param physicalFieldMetaId 可选物理字段元数据主键
     * @return 转换血缘结果，展示映射链路、规则和关联字段
     */
    @GetMapping("/field-transform-rules/lineage")
    public TransformLineageResponse lineage(
            @RequestParam(required = false) Long virtualFieldId,
            @RequestParam(required = false) Long physicalFieldMetaId
    ) {
        return transformService.lineage(virtualFieldId, physicalFieldMetaId);
    }

    private List<Long> entityIds(VirtualKnowledgeBatchRequest request) {
        return request == null || request.getEntityIds() == null ? List.of() : request.getEntityIds();
    }
}
