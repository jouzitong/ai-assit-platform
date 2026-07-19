package ai.platform.aiassit.data.virtualization.core.knowledge;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import ai.platform.aiassit.data.virtualization.core.catalog.VirtualCatalogService;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualFieldEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualRelationEntity;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentCommand;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentDeleteCommand;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentDeleteResult;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentPage;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentPort;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentRef;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentUpsertResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualKnowledgeServiceTest {

    @Mock
    private VirtualCatalogDataRepository repository;
    @Mock
    private VirtualCatalogService catalogService;
    @Mock
    private KnowledgeDocumentPort knowledgeDocumentPort;

    private VirtualKnowledgeService service;
    private VirtualEntityEntity order;

    @BeforeEach
    void setUp() {
        service = new VirtualKnowledgeService(repository, catalogService, knowledgeDocumentPort, new ObjectMapper());
        order = entity(1L, "order", "订单", "订单主数据");
        order.setStatus(CatalogStatus.PUBLISHED);
        order.setEnabled(true);
        order.setCatalogVersion(3L);
        when(repository.entityById(1L)).thenReturn(order);
    }

    @Test
    void previewUsesSemanticModelTemplateAndLeavesUnavailablePropertiesEmpty() {
        order.setDescription("**业务语义**：虚拟表订单主题数据。\n\n**检索线索**：\n- 订单查询");
        VirtualEntityEntity customer = entity(2L, "customer", "客户", "客户主数据");
        VirtualFieldEntity orderCustomerId = field(11L, 1L, "customer_id", "客户ID", LogicalType.LONG);
        VirtualFieldEntity customerId = field(21L, 2L, "id", "客户主键", LogicalType.LONG);
        customerId.setPrimaryKey(true);

        VirtualRelationEntity relation = new VirtualRelationEntity();
        relation.setRelationCode("order_customer");
        relation.setRelationName("订单客户");
        relation.setSourceEntityId(1L);
        relation.setSourceFieldId(11L);
        relation.setTargetEntityId(2L);
        relation.setTargetFieldId(21L);
        relation.setEnabled(true);

        when(repository.fields(1L)).thenReturn(List.of(orderCustomerId));
        when(repository.relations(1L)).thenReturn(List.of(relation));
        when(repository.entityById(2L)).thenReturn(customer);
        when(repository.fieldById(11L)).thenReturn(orderCustomerId);
        when(repository.fieldById(21L)).thenReturn(customerId);

        String content = service.preview(1L).content();

        assertTrue(content.startsWith("---\ndocumentType: data-semantic-model\nmodel: order\n"));
        assertTrue(content.contains("modelAliases: [\"订单\"]"));
        assertTrue(content.contains("domain:\ndescription:"));
        assertTrue(content.contains("sourceRevision: virtual-model/order/v3"));
        assertTrue(content.contains("owner:\n---"));
        assertTrue(content.contains("# 订单（order）"));
        assertTrue(content.contains("## 字段目录（机器可读）"));
        assertTrue(content.contains("\"model\" : \"order\""));
        assertTrue(content.contains("\"code\" : \"customer_id\""));
        assertTrue(content.contains("\"logicalType\" : \"long\""));
        assertTrue(content.contains("\"key\" : \"order_customer\""));
        assertTrue(content.contains("\"model\" : \"customer\""));
        assertTrue(content.contains("\"type\" : \"\""));
        assertTrue(content.contains("\"customer_id\" : \"id\""));
        assertTrue(content.contains("\"fields\" : [ ]"));
        assertTrue(content.contains("## 值域与口径\n\n## 已审核示例"));
        assertFalse(content.contains("虚拟表"));
        assertFalse(content.contains("虚拟字段"));
    }

    @Test
    void initializeCreatesDisabledDraftWithoutOverwritingExistingDocument() {
        order.setStatus(CatalogStatus.DRAFT);
        order.setEnabled(false);
        when(repository.fields(1L)).thenReturn(List.of());
        when(repository.relations(1L)).thenReturn(List.of());
        when(knowledgeDocumentPort.upsert(any())).thenReturn(new KnowledgeDocumentUpsertResult(true, false));

        VirtualKnowledgeSyncResponse response = service.initialize("analytics-kb", List.of(1L));

        ArgumentCaptor<KnowledgeDocumentCommand> captor = ArgumentCaptor.forClass(KnowledgeDocumentCommand.class);
        verify(knowledgeDocumentPort).upsert(captor.capture());
        assertEquals("vt-order", captor.getValue().documentCode());
        assertFalse(captor.getValue().updateAllowed());
        assertFalse(captor.getValue().enabled());
        assertEquals(1, response.createdCount());
    }

    @Test
    void unpublishDeletesKnowledgeDocumentsBeforeReturningEntityToDraft() {
        KnowledgeDocumentRef document = new KnowledgeDocumentRef("vt-order", "analytics-kb");
        when(knowledgeDocumentPort.list(any())).thenReturn(new KnowledgeDocumentPage(List.of(document)));
        when(knowledgeDocumentPort.delete(any())).thenReturn(new KnowledgeDocumentDeleteResult(1, List.of()));

        VirtualUnpublishResponse response = service.unpublish(List.of(1L));

        ArgumentCaptor<KnowledgeDocumentDeleteCommand> deleteCaptor = ArgumentCaptor.forClass(KnowledgeDocumentDeleteCommand.class);
        verify(knowledgeDocumentPort).delete(deleteCaptor.capture());
        assertEquals(List.of("vt-order", "virtual-table/1"), deleteCaptor.getValue().documentCodes());
        assertEquals(CatalogStatus.DRAFT, order.getStatus());
        verify(repository).updateEntity(order);
        verify(catalogService).evict("order");
        assertEquals(1, response.deletedDocumentCount());
    }

    private VirtualEntityEntity entity(Long id, String code, String name, String description) {
        VirtualEntityEntity entity = new VirtualEntityEntity();
        entity.setId(id);
        entity.setEntityCode(code);
        entity.setEntityName(name);
        entity.setDescription(description);
        return entity;
    }

    private VirtualFieldEntity field(Long id, Long entityId, String code, String name, LogicalType type) {
        VirtualFieldEntity field = new VirtualFieldEntity();
        field.setId(id);
        field.setEntityId(entityId);
        field.setFieldCode(code);
        field.setFieldName(name);
        field.setLogicalType(type);
        field.setNullable(false);
        field.setEnabled(true);
        field.setOrdinalPosition(1);
        return field;
    }
}
