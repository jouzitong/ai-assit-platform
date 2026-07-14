package ai.platform.aiassit.data.virtualization.core.catalog;

import ai.platform.aiassit.data.virtualization.api.VirtualCatalogGateway;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCatalogDescriptor;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualFieldEntity;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class VirtualCatalogService implements VirtualCatalogGateway {
    private final CatalogAssembler assembler;
    private final VirtualCatalogDataRepository repository;
    private final Map<String, CatalogSnapshot> cache = new ConcurrentHashMap<>();

    public VirtualCatalogService(CatalogAssembler assembler, VirtualCatalogDataRepository repository) {
        this.assembler = assembler;
        this.repository = repository;
    }

    public CatalogSnapshot requirePublished(String entityCode, Long requestedVersion) {
        VirtualEntityEntity entity = repository.entityByCode(entityCode);
        if (entity == null) {
            throw new VirtualDataException("CATALOG_NOT_FOUND", "虚拟实体不存在: " + entityCode);
        }
        if (entity.getStatus() != CatalogStatus.PUBLISHED || !Boolean.TRUE.equals(entity.getEnabled())) {
            throw new VirtualDataException("CATALOG_NOT_PUBLISHED", "虚拟实体未发布或已停用: " + entityCode);
        }
        long currentVersion = entity.getCatalogVersion() == null ? 0 : entity.getCatalogVersion();
        if (requestedVersion != null && requestedVersion.longValue() != currentVersion) {
            throw new VirtualDataException("CATALOG_VERSION_CONFLICT",
                    "请求目录版本 " + requestedVersion + " 与当前版本 " + currentVersion + " 不一致");
        }
        String key = entity.getEntityCode() + ":" + currentVersion;
        return cache.computeIfAbsent(key, ignored -> assembler.byEntityCode(entityCode));
    }

    @Override
    public VirtualCatalogDescriptor describePublished(String entityCode, Long requestedVersion) {
        CatalogSnapshot snapshot = requirePublished(entityCode, requestedVersion);
        List<VirtualCatalogDescriptor.Relation> relations = snapshot.relations().stream()
                .filter(CatalogSnapshot.Relation::enabled)
                .filter(relation -> snapshot.entityId().equals(relation.sourceEntityId()))
                .collect(Collectors.groupingBy(
                        CatalogSnapshot.Relation::relationCode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet().stream()
                .map(entry -> describeRelation(snapshot, entry.getKey(), entry.getValue()))
                .toList();
        return new VirtualCatalogDescriptor(
                snapshot.entityCode(),
                snapshot.catalogVersion(),
                snapshot.fieldsByCode().values().stream()
                        .map(field -> new VirtualCatalogDescriptor.Field(field.code(), field.primaryKey(), field.enabled()))
                        .toList(),
                relations.stream().map(VirtualCatalogDescriptor.Relation::code).toList(),
                relations
        );
    }

    private VirtualCatalogDescriptor.Relation describeRelation(
            CatalogSnapshot snapshot,
            String relationCode,
            List<CatalogSnapshot.Relation> mappings
    ) {
        if (mappings.isEmpty()) {
            throw new VirtualDataException("CATALOG_RELATION_INVALID", "虚拟关系没有字段映射: " + relationCode);
        }
        CatalogSnapshot.Relation first = mappings.get(0);
        for (CatalogSnapshot.Relation mapping : mappings) {
            if (mapping.resultMode() != first.resultMode()) {
                throw new VirtualDataException("CATALOG_RELATION_INVALID",
                        "同一虚拟关系的结果形态不一致: " + relationCode);
            }
        }
        boolean forward = first.sourceEntityId().equals(snapshot.entityId());
        Long targetEntityId = forward ? first.targetEntityId() : first.sourceEntityId();
        VirtualEntityEntity target = repository.entityById(targetEntityId);
        if (target == null) {
            throw new VirtualDataException("CATALOG_RELATION_TARGET_NOT_FOUND", "虚拟关系目标实体不存在: " + relationCode);
        }
        Map<String, String> fieldMappings = new LinkedHashMap<>();
        for (CatalogSnapshot.Relation mapping : mappings) {
            boolean mappingForward = mapping.sourceEntityId().equals(snapshot.entityId());
            Long localFieldId = mappingForward ? mapping.sourceFieldId() : mapping.targetFieldId();
            Long remoteFieldId = mappingForward ? mapping.targetFieldId() : mapping.sourceFieldId();
            VirtualFieldEntity localField = repository.fieldById(localFieldId);
            VirtualFieldEntity remoteField = repository.fieldById(remoteFieldId);
            if (localField == null || remoteField == null) {
                throw new VirtualDataException("CATALOG_RELATION_FIELD_NOT_FOUND", "虚拟关系字段不存在: " + relationCode);
            }
            fieldMappings.put(localField.getFieldCode(), remoteField.getFieldCode());
        }
        return new VirtualCatalogDescriptor.Relation(
                relationCode, target.getEntityCode(), fieldMappings, first.resultMode()
        );
    }

    public CatalogSnapshot requirePublished(Long entityId) {
        VirtualEntityEntity entity = repository.entityById(entityId);
        if (entity == null) {
            throw new VirtualDataException("CATALOG_NOT_FOUND", "虚拟实体不存在: " + entityId);
        }
        if (entity.getStatus() != CatalogStatus.PUBLISHED || !Boolean.TRUE.equals(entity.getEnabled())) {
            throw new VirtualDataException("CATALOG_NOT_PUBLISHED", "虚拟实体未发布或已停用: " + entityId);
        }
        long currentVersion = entity.getCatalogVersion() == null ? 0 : entity.getCatalogVersion();
        String key = entity.getEntityCode() + ":" + currentVersion;
        return cache.computeIfAbsent(key, ignored -> assembler.byEntityId(entityId));
    }

    public void cache(CatalogSnapshot snapshot) {
        cache.keySet().removeIf(key -> key.startsWith(snapshot.entityCode() + ":"));
        cache.put(snapshot.entityCode() + ":" + snapshot.catalogVersion(), snapshot);
    }

    public void evict(String entityCode) {
        if (entityCode != null) {
            cache.keySet().removeIf(key -> key.startsWith(entityCode + ":"));
        }
    }
}
