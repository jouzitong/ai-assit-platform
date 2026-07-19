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

/** DB Engine 应用层承载的虚拟目录管理入口。 */
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

    @PostMapping("/entities/from-physical-table")
    public CatalogSnapshot createFromPhysicalTable(@RequestBody CreateVirtualEntityFromTableRequest request) {
        return draftFactory.create(request);
    }

    @PostMapping("/publish")
    public CatalogSnapshot publish(@RequestParam Long entityId) {
        return publisher.publish(entityId);
    }

    @PostMapping("/publish-batch")
    public List<CatalogSnapshot> publishBatch(@RequestBody VirtualKnowledgeBatchRequest request) {
        return entityIds(request).stream().distinct().map(publisher::publish).toList();
    }

    @PostMapping("/entities/description/generate")
    public VirtualDescriptionGenerateResponse generateDescription(@RequestBody VirtualDescriptionGenerateRequest request) {
        return descriptionService.generate(request);
    }

    @GetMapping("/knowledge-preview")
    public VirtualKnowledgePreviewResponse knowledgePreview(@RequestParam Long entityId) {
        return knowledgeService.preview(entityId);
    }

    @PostMapping("/knowledge-status")
    public List<VirtualKnowledgeStatusItem> knowledgeStatus(@RequestBody VirtualKnowledgeBatchRequest request) {
        return knowledgeService.status(entityIds(request));
    }

    @GetMapping("/knowledge-configuration")
    public VirtualKnowledgeConfigurationResponse knowledgeConfiguration() {
        return new VirtualKnowledgeConfigurationResponse(
                DbEngineSystemSettingKeys.KNOWLEDGE_BASE_CODE,
                knowledgeBaseSettingResolver.resolve()
        );
    }

    @PostMapping("/knowledge-sync")
    public VirtualKnowledgeSyncResponse knowledgeSync(@RequestBody VirtualKnowledgeBatchRequest request) {
        return knowledgeService.initialize(knowledgeBaseSettingResolver.resolve(), entityIds(request));
    }

    @PostMapping("/knowledge-initialize")
    public VirtualKnowledgeSyncResponse knowledgeInitialize(@RequestBody VirtualKnowledgeBatchRequest request) {
        return knowledgeService.initialize(knowledgeBaseSettingResolver.resolve(), entityIds(request));
    }

    @PostMapping("/unpublish-check")
    public List<VirtualKnowledgeStatusItem> unpublishCheck(@RequestBody VirtualKnowledgeBatchRequest request) {
        return knowledgeService.status(entityIds(request));
    }

    @PostMapping("/unpublish")
    public VirtualUnpublishResponse unpublish(@RequestBody VirtualKnowledgeBatchRequest request) {
        return knowledgeService.unpublish(entityIds(request));
    }

    @PostMapping("/validate")
    public void validateCatalog(@RequestParam Long entityId) {
        publisher.validate(entityId);
    }

    @GetMapping("/field-transformers")
    public List<FieldTransformerRegistry.Descriptor> transformers() {
        return transformService.transformers();
    }

    @PostMapping("/field-transform-rules/validate")
    public void validateRule(@RequestParam Long ruleId) {
        transformService.validate(ruleId);
    }

    @PostMapping("/field-transform-rules/script/generate")
    public FieldTransformScriptGenerateResponse generateFieldTransformScript(
            @RequestBody FieldTransformScriptGenerateRequest request
    ) {
        return scriptService.generate(request);
    }

    @PostMapping("/field-transform-rules/preview")
    public TransformPreviewResponse preview(@RequestBody TransformPreviewRequest request) {
        return transformService.preview(request);
    }

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
