package ai.platform.aiassit.data.virtualization.core.knowledge;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.core.catalog.VirtualCatalogService;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualFieldEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualRelationEntity;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import ai.platform.aiassit.service.ai.api.AiKnowledgeApi;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentBatchRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentDeleteResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassit.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentType;
import lombok.extern.slf4j.Slf4j;
import org.athena.framework.web.vo.R;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VirtualKnowledgeService {

    private static final String SOURCE_SYSTEM = "dataVirtualization";
    private static final String DOCUMENT_PREFIX = "virtual-table/";

    private final VirtualCatalogDataRepository repository;
    private final VirtualCatalogService catalogService;
    private final AiKnowledgeApi aiKnowledgeApi;

    public VirtualKnowledgeService(
            VirtualCatalogDataRepository repository,
            VirtualCatalogService catalogService,
            AiKnowledgeApi aiKnowledgeApi
    ) {
        this.repository = repository;
        this.catalogService = catalogService;
        this.aiKnowledgeApi = aiKnowledgeApi;
    }

    public VirtualKnowledgePreviewResponse preview(Long entityId) {
        VirtualEntityEntity entity = requireEntity(entityId);
        List<VirtualFieldEntity> fields = repository.fields(entityId).stream()
                .filter(field -> !Boolean.FALSE.equals(field.getEnabled()))
                .sorted(Comparator.comparing(VirtualFieldEntity::getOrdinalPosition, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        List<VirtualRelationEntity> relations = repository.relations(entityId).stream()
                .filter(relation -> !Boolean.FALSE.equals(relation.getEnabled()))
                .toList();
        return new VirtualKnowledgePreviewResponse("markdown", buildMarkdown(entity, fields, relations));
    }

    public List<VirtualKnowledgeStatusItem> status(List<Long> entityIds) {
        List<Long> ids = normalizeIds(entityIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, Long> entityIdByDocumentCode = new LinkedHashMap<>();
        ids.forEach(id -> entityIdByDocumentCode.put(documentCode(id), id));

        List<AiKbDocumentListItemDTO> documents = findDocuments(entityIdByDocumentCode.keySet());
        Map<Long, Set<String>> kbCodesByEntityId = new LinkedHashMap<>();
        documents.forEach(document -> {
            Long entityId = entityIdByDocumentCode.get(document.getDocumentCode());
            if (entityId != null && StringUtils.hasText(document.getKbCode())) {
                kbCodesByEntityId.computeIfAbsent(entityId, ignored -> new LinkedHashSet<>()).add(document.getKbCode());
            }
        });
        return ids.stream()
                .map(id -> new VirtualKnowledgeStatusItem(id, new ArrayList<>(kbCodesByEntityId.getOrDefault(id, Set.of()))))
                .toList();
    }

    public VirtualKnowledgeSyncResponse sync(VirtualKnowledgeSyncRequest request) {
        if (request == null || !StringUtils.hasText(request.getKbCode())) {
            throw new VirtualDataException("KNOWLEDGE_BASE_REQUIRED", "请选择目标知识库");
        }
        List<Long> entityIds = normalizeIds(request.getEntityIds());
        if (entityIds.isEmpty()) {
            throw new VirtualDataException("VIRTUAL_ENTITY_REQUIRED", "请选择要同步的虚拟表");
        }

        String kbCode = request.getKbCode().trim();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (Long entityId : entityIds) {
            VirtualEntityEntity entity = requirePublishedEntity(entityId);
            AiKbDocumentUpsertResponse result = upsert(kbCode, entity, preview(entityId).content());
            if (Boolean.TRUE.equals(result.getCreated())) {
                created++;
            } else if (Boolean.TRUE.equals(result.getUpdated())) {
                updated++;
            } else {
                unchanged++;
            }
        }
        return new VirtualKnowledgeSyncResponse(kbCode, entityIds.size(), created, updated, unchanged);
    }

    public VirtualUnpublishResponse unpublish(List<Long> entityIds) {
        List<Long> ids = normalizeIds(entityIds);
        if (ids.isEmpty()) {
            throw new VirtualDataException("VIRTUAL_ENTITY_REQUIRED", "请选择要取消发布的虚拟表");
        }

        List<String> documentCodes = ids.stream().map(this::documentCode).toList();
        int deletedDocuments = deleteDocuments(documentCodes);
        int unpublished = 0;
        for (Long entityId : ids) {
            VirtualEntityEntity entity = requireEntity(entityId);
            if (entity.getStatus() == CatalogStatus.PUBLISHED) {
                entity.setStatus(CatalogStatus.DRAFT);
                repository.updateEntity(entity);
                catalogService.evict(entity.getEntityCode());
                unpublished++;
            }
        }
        return new VirtualUnpublishResponse(unpublished, deletedDocuments);
    }

    private AiKbDocumentUpsertResponse upsert(String kbCode, VirtualEntityEntity entity, String content) {
        AiKbDocumentUpsertRequest request = new AiKbDocumentUpsertRequest();
        request.setKbId(kbCode);
        request.setDocumentId(documentCode(entity.getId()));
        request.setDocumentName(documentName(entity));
        request.setDocumentType(AiKbDocumentType.DB_TABLE);
        request.setBizType(AiKbBizType.DB_DATA_SOURCE);
        request.setContent(content);
        request.setCanUpdate(Boolean.TRUE);

        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("sourceSystem", SOURCE_SYSTEM);
        ext.put("virtualEntityId", entity.getId());
        ext.put("virtualTableKey", entity.getEntityCode());
        ext.put("virtualTableName", entity.getEntityName());
        ext.put("catalogVersion", entity.getCatalogVersion());
        request.setExt(ext);

        R<AiKbDocumentUpsertResponse> response = aiKnowledgeApi.upsertDocument(request);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            log.error("virtual knowledge upsert failed, kbCode={}, entityId={}, responseCode={}",
                    kbCode, entity.getId(), response == null ? null : response.getCode());
            throw new VirtualDataException("KNOWLEDGE_SYNC_FAILED", "虚拟表知识文档同步失败: " + entity.getEntityCode());
        }
        return response.getData();
    }

    private List<AiKbDocumentListItemDTO> findDocuments(Iterable<String> documentCodes) {
        AiKbDocumentBatchRequest request = new AiKbDocumentBatchRequest();
        documentCodes.forEach(request.getDocumentCodes()::add);
        if (request.getDocumentCodes().isEmpty()) {
            return List.of();
        }
        R<List<AiKbDocumentListItemDTO>> response = aiKnowledgeApi.listDocuments(request);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            throw new VirtualDataException("KNOWLEDGE_STATUS_FAILED", "知识库同步状态查询失败");
        }
        return response.getData();
    }

    private int deleteDocuments(List<String> documentCodes) {
        if (findDocuments(documentCodes).isEmpty()) {
            return 0;
        }
        AiKbDocumentBatchRequest request = new AiKbDocumentBatchRequest();
        request.setDocumentCodes(documentCodes);
        R<AiKbDocumentDeleteResponse> response = aiKnowledgeApi.deleteDocuments(request);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            throw new VirtualDataException("KNOWLEDGE_DELETE_FAILED", "取消发布前删除知识文档失败");
        }
        if (!response.getData().getSkippedDocumentCodes().isEmpty()) {
            throw new VirtualDataException("KNOWLEDGE_DELETE_INCOMPLETE",
                    "部分知识文档未能删除: " + String.join(", ", response.getData().getSkippedDocumentCodes()));
        }
        return response.getData().getDeletedCount();
    }

    private String buildMarkdown(
            VirtualEntityEntity entity,
            List<VirtualFieldEntity> fields,
            List<VirtualRelationEntity> relations
    ) {
        Map<Long, Set<String>> relatedFields = relatedFields(entity, relations);
        StringBuilder content = new StringBuilder();
        content.append("# ").append(markdown(firstNonBlank(entity.getEntityName(), entity.getEntityCode()))).append("\n\n");
        content.append("## 数据表定义\n");
        content.append("- **表 Key**: `").append(markdown(entity.getEntityCode())).append("`\n");
        content.append("- **名称**: ").append(markdown(firstNonBlank(entity.getEntityName(), entity.getEntityCode()))).append("\n");
        content.append("- **目录版本**: ").append(entity.getCatalogVersion() == null ? 0 : entity.getCatalogVersion()).append("\n\n");
        content.append("### 说明\n\n");
        content.append(knowledgeDescription(entity.getDescription())).append("\n\n");

        content.append("## 字段\n");
        content.append("| 字段 Key | 字段名称 | 逻辑类型 | 约束 | 关联字段 | 说明 |\n");
        content.append("|---|---|---|---|---|---|\n");
        for (VirtualFieldEntity field : fields) {
            content.append("| ").append(markdown(field.getFieldCode()))
                    .append(" | ").append(markdown(firstNonBlank(field.getFieldName(), field.getFieldCode())))
                    .append(" | ").append(markdown(field.getLogicalType() == null ? "-" : field.getLogicalType().getName()))
                    .append(" | ").append(markdown(fieldConstraint(field)))
                    .append(" | ").append(relatedFieldLabel(relatedFields.get(field.getId())))
                    .append(" | ").append(markdown(firstNonBlank(field.getRemark(), "-")))
                    .append(" |\n");
        }
        if (fields.isEmpty()) {
            content.append("| - | - | - | - | - | 暂无字段 |\n");
        }
        return content.toString().trim();
    }

    private Map<Long, Set<String>> relatedFields(
            VirtualEntityEntity entity,
            List<VirtualRelationEntity> relations
    ) {
        Map<Long, Set<String>> relatedFields = new LinkedHashMap<>();
        for (VirtualRelationEntity relation : relations) {
            boolean outgoing = Objects.equals(entity.getId(), relation.getSourceEntityId());
            Long localFieldId = outgoing ? relation.getSourceFieldId() : relation.getTargetFieldId();
            Long remoteEntityId = outgoing ? relation.getTargetEntityId() : relation.getSourceEntityId();
            Long remoteFieldId = outgoing ? relation.getTargetFieldId() : relation.getSourceFieldId();
            VirtualEntityEntity remoteEntity = repository.entityById(remoteEntityId);
            VirtualFieldEntity remoteField = repository.fieldById(remoteFieldId);
            if (localFieldId == null || remoteEntity == null || remoteField == null
                    || !StringUtils.hasText(remoteEntity.getEntityCode()) || !StringUtils.hasText(remoteField.getFieldCode())) {
                continue;
            }
            relatedFields.computeIfAbsent(localFieldId, ignored -> new LinkedHashSet<>())
                    .add(remoteEntity.getEntityCode().trim() + "." + remoteField.getFieldCode().trim());
        }
        return relatedFields;
    }

    private String relatedFieldLabel(Set<String> targets) {
        if (targets == null || targets.isEmpty()) return "-";
        return targets.stream()
                .map(target -> "`" + markdown(target) + "`")
                .collect(Collectors.joining("、"));
    }

    private String knowledgeDescription(String description) {
        return firstNonBlank(description, "暂无说明")
                .replace("虚拟表", "数据表")
                .replace("虚拟字段", "字段")
                .replace("虚拟实体", "数据对象")
                .replace("虚拟对象", "数据对象");
    }

    private VirtualEntityEntity requireEntity(Long entityId) {
        VirtualEntityEntity entity = repository.entityById(entityId);
        if (entity == null) {
            throw new VirtualDataException("CATALOG_NOT_FOUND", "虚拟表不存在: " + entityId);
        }
        return entity;
    }

    private VirtualEntityEntity requirePublishedEntity(Long entityId) {
        VirtualEntityEntity entity = requireEntity(entityId);
        if (entity.getStatus() != CatalogStatus.PUBLISHED || !Boolean.TRUE.equals(entity.getEnabled())) {
            throw new VirtualDataException("CATALOG_NOT_PUBLISHED", "仅已发布且启用的虚拟表可同步知识库: " + entity.getEntityCode());
        }
        return entity;
    }

    private List<Long> normalizeIds(List<Long> entityIds) {
        return entityIds == null ? List.of() : entityIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String documentCode(Long entityId) {
        return DOCUMENT_PREFIX + entityId;
    }

    private String documentName(VirtualEntityEntity entity) {
        String name = firstNonBlank(entity.getEntityName(), entity.getEntityCode());
        return Objects.equals(name, entity.getEntityCode()) ? name : entity.getEntityCode() + " - " + name;
    }

    private String fieldConstraint(VirtualFieldEntity field) {
        List<String> constraints = new ArrayList<>();
        if (Boolean.TRUE.equals(field.getPrimaryKey())) constraints.add("主键");
        constraints.add(Boolean.TRUE.equals(field.getNullable()) ? "可空" : "非空");
        return String.join("；", constraints);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return "";
    }

    private String markdown(String value) {
        return StringUtils.hasText(value) ? value.trim().replace("|", "\\|").replace("\r", " ").replace("\n", " ") : "-";
    }
}
