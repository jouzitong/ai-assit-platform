package ai.platform.aiassit.data.virtualization.core.knowledge;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.core.catalog.VirtualCatalogService;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualFieldEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualRelationEntity;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentCommand;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentDeleteCommand;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentDeleteResult;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentPage;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentPort;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentQuery;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentRef;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentUpsertResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class VirtualKnowledgeService {

    private static final String SOURCE_SYSTEM = "dataVirtualization";
    private static final String DOCUMENT_PREFIX = "vt-";
    private static final String LEGACY_DOCUMENT_PREFIX = "virtual-table/";

    private final VirtualCatalogDataRepository repository;
    private final VirtualCatalogService catalogService;
    private final KnowledgeDocumentPort knowledgeDocumentPort;
    private final ObjectMapper objectMapper;

    public VirtualKnowledgeService(
            VirtualCatalogDataRepository repository,
            VirtualCatalogService catalogService,
            KnowledgeDocumentPort knowledgeDocumentPort,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.catalogService = catalogService;
        this.knowledgeDocumentPort = knowledgeDocumentPort;
        this.objectMapper = objectMapper;
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
        ids.forEach(id -> {
            VirtualEntityEntity entity = repository.entityById(id);
            if (entity != null && StringUtils.hasText(entity.getEntityCode())) {
                entityIdByDocumentCode.put(documentCode(entity), id);
                entityIdByDocumentCode.put(legacyDocumentCode(entity), id);
            }
        });

        List<KnowledgeDocumentRef> documents = findDocuments(entityIdByDocumentCode.keySet());
        Map<Long, Set<String>> kbCodesByEntityId = new LinkedHashMap<>();
        documents.forEach(document -> {
            Long entityId = entityIdByDocumentCode.get(document.documentCode());
            if (entityId != null && StringUtils.hasText(document.knowledgeBaseCode())) {
                kbCodesByEntityId.computeIfAbsent(entityId, ignored -> new LinkedHashSet<>()).add(document.knowledgeBaseCode());
            }
        });
        return ids.stream()
                .map(id -> new VirtualKnowledgeStatusItem(id, new ArrayList<>(kbCodesByEntityId.getOrDefault(id, Set.of()))))
                .toList();
    }

    public VirtualKnowledgeSyncResponse initialize(String knowledgeBaseCode, List<Long> requestedEntityIds) {
        if (!StringUtils.hasText(knowledgeBaseCode)) {
            throw new VirtualDataException("KNOWLEDGE_BASE_REQUIRED", "未配置目标知识库");
        }
        List<Long> entityIds = normalizeIds(requestedEntityIds);
        if (entityIds.isEmpty()) {
            throw new VirtualDataException("VIRTUAL_ENTITY_REQUIRED", "请选择要初始化知识文档的虚拟表");
        }

        String kbCode = knowledgeBaseCode.trim();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (Long entityId : entityIds) {
            VirtualEntityEntity entity = requireEntity(entityId);
            KnowledgeDocumentUpsertResult result = upsert(kbCode, entity, preview(entityId).content());
            if (result.created()) {
                created++;
            } else if (result.updated()) {
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

        List<VirtualEntityEntity> entities = ids.stream().map(this::requireEntity).toList();
        List<String> documentCodes = entities.stream()
                .flatMap(entity -> List.of(documentCode(entity), legacyDocumentCode(entity)).stream())
                .toList();
        int deletedDocuments = deleteDocuments(documentCodes);
        int unpublished = 0;
        for (VirtualEntityEntity entity : entities) {
            if (entity.getStatus() == CatalogStatus.PUBLISHED) {
                entity.setStatus(CatalogStatus.DRAFT);
                repository.updateEntity(entity);
                catalogService.evict(entity.getEntityCode());
                unpublished++;
            }
        }
        return new VirtualUnpublishResponse(unpublished, deletedDocuments);
    }

    private KnowledgeDocumentUpsertResult upsert(String kbCode, VirtualEntityEntity entity, String content) {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("sourceSystem", SOURCE_SYSTEM);
        ext.put("virtualEntityId", entity.getId());
        ext.put("virtualTableKey", entity.getEntityCode());
        ext.put("virtualTableName", entity.getEntityName());
        ext.put("catalogVersion", entity.getCatalogVersion());
        KnowledgeDocumentCommand command = new KnowledgeDocumentCommand(
                kbCode,
                documentCode(entity),
                documentName(entity),
                content,
                false,
                false,
                ext
        );
        KnowledgeDocumentUpsertResult result = knowledgeDocumentPort.upsert(command);
        if (result == null) {
            log.error("virtual knowledge initialize failed, kbCode={}, entityId={}", kbCode, entity.getId());
            throw new VirtualDataException("KNOWLEDGE_INITIALIZE_FAILED", "虚拟表知识文档初始化失败: " + entity.getEntityCode());
        }
        return result;
    }

    private List<KnowledgeDocumentRef> findDocuments(Iterable<String> documentCodes) {
        List<String> codes = new ArrayList<>();
        documentCodes.forEach(codes::add);
        if (codes.isEmpty()) {
            return List.of();
        }
        KnowledgeDocumentPage page = knowledgeDocumentPort.list(new KnowledgeDocumentQuery(codes));
        if (page == null) {
            throw new VirtualDataException("KNOWLEDGE_STATUS_FAILED", "知识库同步状态查询失败");
        }
        return page.documents();
    }

    private int deleteDocuments(List<String> documentCodes) {
        if (findDocuments(documentCodes).isEmpty()) {
            return 0;
        }
        KnowledgeDocumentDeleteResult result = knowledgeDocumentPort.delete(new KnowledgeDocumentDeleteCommand(documentCodes));
        if (result == null) {
            throw new VirtualDataException("KNOWLEDGE_DELETE_FAILED", "取消发布前删除知识文档失败");
        }
        if (!result.skippedDocumentCodes().isEmpty()) {
            throw new VirtualDataException("KNOWLEDGE_DELETE_INCOMPLETE",
                    "部分知识文档未能删除: " + String.join(", ", result.skippedDocumentCodes()));
        }
        return result.deletedCount();
    }

    private String buildMarkdown(
            VirtualEntityEntity entity,
            List<VirtualFieldEntity> fields,
            List<VirtualRelationEntity> relations
    ) {
        StringBuilder content = new StringBuilder();
        content.append("---\n");
        content.append("documentType: data-semantic-model\n");
        content.append("model: ").append(entity.getEntityCode()).append("\n");
        content.append("modelAliases: ").append(yamlAliases(entity)).append("\n");
        content.append("domain:\n");
        content.append("description: ").append(yamlValue(knowledgeDescription(entity.getDescription()))).append("\n");
        content.append("sourceRevision: ").append(sourceRevision(entity)).append("\n");
        content.append("updatedAt: ").append(LocalDate.now()).append("\n");
        content.append("owner:\n");
        content.append("---\n\n");
        content.append("# ").append(markdown(firstNonBlank(entity.getEntityName(), entity.getEntityCode())))
                .append("（").append(markdown(entity.getEntityCode())).append("）\n\n");
        content.append("## 字段目录（机器可读）\n\n");
        content.append("```json\n").append(machineReadableCatalog(entity, fields, relations)).append("\n```\n\n");
        content.append("## 值域与口径\n\n");
        content.append("## 已审核示例\n");
        return content.toString().trim();
    }

    private String machineReadableCatalog(
            VirtualEntityEntity entity,
            List<VirtualFieldEntity> fields,
            List<VirtualRelationEntity> relations
    ) {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("schemaVersion", "1.0");
        catalog.put("model", entity.getEntityCode());
        catalog.put("primaryKeys", fields.stream()
                .filter(field -> Boolean.TRUE.equals(field.getPrimaryKey()))
                .map(VirtualFieldEntity::getFieldCode)
                .toList());
        catalog.put("defaultTimeField", "");
        catalog.put("fields", fields.stream().map(this::fieldCatalogItem).toList());
        catalog.put("relations", relationCatalogItems(entity, relations));
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(catalog);
        } catch (JsonProcessingException exception) {
            throw new VirtualDataException("KNOWLEDGE_DOCUMENT_RENDER_FAILED", "虚拟表知识文档生成失败: " + entity.getEntityCode());
        }
    }

    private Map<String, Object> fieldCatalogItem(VirtualFieldEntity field) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", firstNonBlank(field.getFieldCode()));
        item.put("name", firstNonBlank(field.getFieldName(), field.getFieldCode()));
        item.put("aliases", List.of());
        item.put("logicalType", logicalType(field));
        item.put("description", firstNonBlank(field.getRemark()));
        item.put("filterOperators", List.of());
        item.put("example", null);
        return item;
    }

    private List<Map<String, Object>> relationCatalogItems(
            VirtualEntityEntity entity,
            List<VirtualRelationEntity> relations
    ) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (VirtualRelationEntity relation : relations) {
            boolean outgoing = Objects.equals(entity.getId(), relation.getSourceEntityId());
            Long localFieldId = outgoing ? relation.getSourceFieldId() : relation.getTargetFieldId();
            Long remoteEntityId = outgoing ? relation.getTargetEntityId() : relation.getSourceEntityId();
            Long remoteFieldId = outgoing ? relation.getTargetFieldId() : relation.getSourceFieldId();
            VirtualEntityEntity remoteEntity = repository.entityById(remoteEntityId);
            VirtualFieldEntity localField = repository.fieldById(localFieldId);
            VirtualFieldEntity remoteField = repository.fieldById(remoteFieldId);
            if (remoteEntity == null || !StringUtils.hasText(remoteEntity.getEntityCode())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", firstNonBlank(relation.getRelationCode(), remoteEntity.getEntityCode()));
            item.put("model", remoteEntity.getEntityCode());
            item.put("type", "");
            Map<String, String> on = new LinkedHashMap<>();
            if (localField != null && remoteField != null
                    && StringUtils.hasText(localField.getFieldCode()) && StringUtils.hasText(remoteField.getFieldCode())) {
                on.put(localField.getFieldCode(), remoteField.getFieldCode());
            }
            item.put("on", on);
            item.put("fields", List.of());
            items.add(item);
        }
        return items;
    }

    private String logicalType(VirtualFieldEntity field) {
        if (field.getLogicalType() == null) return "";
        return switch (field.getLogicalType()) {
            case STRING -> "string";
            case BOOLEAN -> "boolean";
            case INTEGER -> "integer";
            case LONG -> "long";
            case DECIMAL -> "decimal";
            case DATE -> "date";
            case TIMESTAMP -> "datetime";
            case JSON -> "json";
            case BINARY -> "binary";
        };
    }

    private String knowledgeDescription(String description) {
        return firstNonBlank(description)
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

    private List<Long> normalizeIds(List<Long> entityIds) {
        return entityIds == null ? List.of() : entityIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String documentCode(VirtualEntityEntity entity) {
        return DOCUMENT_PREFIX + entity.getEntityCode().trim();
    }

    private String legacyDocumentCode(VirtualEntityEntity entity) {
        return LEGACY_DOCUMENT_PREFIX + entity.getId();
    }

    private String documentName(VirtualEntityEntity entity) {
        String name = firstNonBlank(entity.getEntityName(), entity.getEntityCode());
        return Objects.equals(name, entity.getEntityCode()) ? name : entity.getEntityCode() + " - " + name;
    }

    private String yamlAliases(VirtualEntityEntity entity) {
        if (!StringUtils.hasText(entity.getEntityName()) || Objects.equals(entity.getEntityName().trim(), entity.getEntityCode().trim())) {
            return "[]";
        }
        return "[" + yamlValue(entity.getEntityName()) + "]";
    }

    private String yamlValue(String value) {
        if (!StringUtils.hasText(value)) return "";
        return "\"" + value.trim()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", "\\n") + "\"";
    }

    private String sourceRevision(VirtualEntityEntity entity) {
        if (entity.getCatalogVersion() == null) return "";
        return "virtual-model/" + entity.getEntityCode().trim() + "/v" + entity.getCatalogVersion();
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
