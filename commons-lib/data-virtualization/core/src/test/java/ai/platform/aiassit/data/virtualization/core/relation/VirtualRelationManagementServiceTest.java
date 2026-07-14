package ai.platform.aiassit.data.virtualization.core.relation;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RelationResultMode;
import ai.platform.aiassit.data.virtualization.data.dto.VirtualRelationDTO;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualFieldEntity;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import ai.platform.aiassit.data.virtualization.data.service.VirtualRelationService;
import ai.platform.aiassit.data.virtualization.spi.text.TextGenerationPort;
import ai.platform.aiassit.data.virtualization.spi.text.TextGenerationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VirtualRelationManagementServiceTest {

    @Test
    void shouldReturnValidatedAiSuggestionsWithoutSavingThem() {
        VirtualCatalogDataRepository repository = mock(VirtualCatalogDataRepository.class);
        VirtualRelationService relationService = mock(VirtualRelationService.class);
        TextGenerationPort textGenerationPort = mock(TextGenerationPort.class);
        VirtualEntityEntity orders = entity(1L, "orders");
        VirtualEntityEntity customers = entity(2L, "customers");
        VirtualFieldEntity customerId = field(11L, 1L, "customer_id", LogicalType.LONG);
        VirtualFieldEntity id = field(21L, 2L, "id", LogicalType.LONG);
        when(repository.entityById(1L)).thenReturn(orders);
        when(repository.entityById(2L)).thenReturn(customers);
        when(repository.fields(1L)).thenReturn(List.of(customerId));
        when(repository.fields(2L)).thenReturn(List.of(id));
        when(repository.relations(1L)).thenReturn(List.of());
        when(repository.relations(2L)).thenReturn(List.of());
        when(textGenerationPort.generate(any())).thenReturn(new TextGenerationResult("""
                ```json
                [{
                  "relationCode": "orders_customer",
                  "relationName": "订单客户",
                  "sourceEntityId": 1,
                  "sourceFieldId": 11,
                  "targetEntityId": 2,
                  "targetFieldId": 21,
                  "resultMode": "OBJECT",
                  "reason": "customer_id 与客户主键匹配",
                  "confidence": 0.96
                }]
                ```
                """));

        VirtualRelationManagementService service = new VirtualRelationManagementService(
                repository, relationService, textGenerationPort, new ObjectMapper());
        VirtualRelationSuggestRequest request = new VirtualRelationSuggestRequest();
        request.setEntityIds(List.of(1L, 2L));

        List<VirtualRelationSuggestion> suggestions = service.suggest(request);

        assertEquals(1, suggestions.size());
        assertEquals(RelationResultMode.OBJECT, suggestions.get(0).relation().getResultMode());
        assertEquals(0.96D, suggestions.get(0).confidence());
        verifyNoInteractions(relationService);
    }

    @Test
    void shouldPersistAllBatchChangesThroughOneServiceBoundary() {
        VirtualCatalogDataRepository repository = mock(VirtualCatalogDataRepository.class);
        VirtualRelationService relationService = mock(VirtualRelationService.class);
        TextGenerationPort textGenerationPort = mock(TextGenerationPort.class);
        VirtualEntityEntity orders = entity(1L, "orders");
        VirtualEntityEntity customers = entity(2L, "customers");
        VirtualFieldEntity customerId = field(11L, 1L, "customer_id", LogicalType.LONG);
        VirtualFieldEntity id = field(21L, 2L, "id", LogicalType.LONG);
        when(repository.entityById(1L)).thenReturn(orders);
        when(repository.entityById(2L)).thenReturn(customers);
        when(repository.fieldById(11L)).thenReturn(customerId);
        when(repository.fieldById(21L)).thenReturn(id);

        VirtualRelationDTO existing = relation(100L);
        VirtualRelationDTO updated = relation(101L);
        VirtualRelationDTO created = relation(null);
        when(relationService.get(100L)).thenReturn(existing);
        when(relationService.get(101L)).thenReturn(updated);
        when(relationService.delete(100L)).thenReturn(true);
        when(relationService.update(101L, updated)).thenReturn(updated);
        when(relationService.add(created)).thenReturn(created);

        VirtualRelationBatchSaveRequest request = new VirtualRelationBatchSaveRequest();
        request.setDeletes(List.of(100L));
        request.setUpdates(List.of(updated));
        request.setCreates(List.of(created));
        VirtualRelationManagementService service = new VirtualRelationManagementService(
                repository, relationService, textGenerationPort, new ObjectMapper());

        VirtualRelationBatchSaveResponse result = service.saveBatch(request);

        assertEquals(1, result.createdCount());
        assertEquals(1, result.updatedCount());
        assertEquals(1, result.deletedCount());
        verify(relationService).delete(100L);
        verify(relationService).update(101L, updated);
        verify(relationService).add(created);
        verify(textGenerationPort, never()).generate(any());
    }

    private VirtualRelationDTO relation(Long id) {
        VirtualRelationDTO relation = new VirtualRelationDTO();
        relation.setId(id);
        relation.setRelationCode("orders_customer");
        relation.setRelationName("订单客户");
        relation.setResultMode(RelationResultMode.OBJECT);
        relation.setSourceEntityId(1L);
        relation.setSourceFieldId(11L);
        relation.setTargetEntityId(2L);
        relation.setTargetFieldId(21L);
        relation.setEnabled(true);
        return relation;
    }

    private VirtualEntityEntity entity(Long id, String code) {
        VirtualEntityEntity entity = new VirtualEntityEntity();
        entity.setId(id);
        entity.setEntityCode(code);
        entity.setEntityName(code);
        entity.setEnabled(true);
        return entity;
    }

    private VirtualFieldEntity field(Long id, Long entityId, String code, LogicalType logicalType) {
        VirtualFieldEntity field = new VirtualFieldEntity();
        field.setId(id);
        field.setEntityId(entityId);
        field.setFieldCode(code);
        field.setFieldName(code);
        field.setLogicalType(logicalType);
        field.setEnabled(true);
        return field;
    }
}
