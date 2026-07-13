package ai.platform.aiassit.data.virtualization.core.catalog;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class VirtualCatalogPublisher {
    private final CatalogAssembler assembler;
    private final CatalogValidator validator;
    private final VirtualCatalogDataRepository repository;
    private final VirtualCatalogService catalogService;

    public VirtualCatalogPublisher(
            CatalogAssembler assembler,
            CatalogValidator validator,
            VirtualCatalogDataRepository repository,
            VirtualCatalogService catalogService
    ) {
        this.assembler = assembler;
        this.validator = validator;
        this.repository = repository;
        this.catalogService = catalogService;
    }

    @Transactional(rollbackFor = Exception.class)
    public CatalogSnapshot publish(Long entityId) {
        CatalogSnapshot draft = assembler.byEntityId(entityId);
        validator.validate(draft);
        VirtualEntityEntity entity = repository.entityById(entityId);
        entity.setStatus(CatalogStatus.PUBLISHED);
        entity.setCatalogVersion((entity.getCatalogVersion() == null ? 0 : entity.getCatalogVersion()) + 1);
        entity.setEnabled(true);
        repository.updateEntity(entity);
        CatalogSnapshot published = assembler.byEntityId(entityId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    catalogService.cache(published);
                }
            });
        } else {
            catalogService.cache(published);
        }
        return published;
    }

    public void validate(Long entityId) {
        validator.validate(assembler.byEntityId(entityId));
    }
}
