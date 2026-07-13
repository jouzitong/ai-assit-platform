package ai.platform.aiassit.data.virtualization.core.controller;

import ai.platform.aiassit.data.virtualization.api.dto.TransformLineageResponse;
import ai.platform.aiassit.data.virtualization.api.dto.TransformPreviewRequest;
import ai.platform.aiassit.data.virtualization.api.dto.TransformPreviewResponse;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.catalog.CreateVirtualEntityFromTableRequest;
import ai.platform.aiassit.data.virtualization.core.catalog.VirtualCatalogPublisher;
import ai.platform.aiassit.data.virtualization.core.catalog.VirtualEntityDraftFactory;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformManagementService;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformerRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/virtual-data")
public class VirtualCatalogManagementController {
    private final VirtualEntityDraftFactory draftFactory;
    private final VirtualCatalogPublisher publisher;
    private final FieldTransformManagementService transformService;

    public VirtualCatalogManagementController(
            VirtualEntityDraftFactory draftFactory,
            VirtualCatalogPublisher publisher,
            FieldTransformManagementService transformService
    ) {
        this.draftFactory = draftFactory;
        this.publisher = publisher;
        this.transformService = transformService;
    }

    @PostMapping("/entities/from-physical-table")
    public CatalogSnapshot createFromPhysicalTable(@RequestBody CreateVirtualEntityFromTableRequest request) {
        return draftFactory.create(request);
    }

    @PostMapping("/publish")
    public CatalogSnapshot publish(@RequestParam Long entityId) {
        return publisher.publish(entityId);
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
}
