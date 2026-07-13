package ai.platform.aiassit.data.virtualization.core.catalog;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VirtualCatalogService {
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
}
