package ai.platform.aiassit.db.engine.virtualization.adapter.compat;

import ai.platform.aiassit.data.virtualization.api.VirtualCatalogGateway;
import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCatalogDescriptor;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.AggregateFunction;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RelationResultMode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.SortDirection;
import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountDimension;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountMetric;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryFilterCondition;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetExt;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryListRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryRelation;
import ai.platform.aiassit.db.engine.api.dto.DbQuerySort;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeExt;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyRequestTranslatorTest {

    private VirtualCatalogGateway catalogGateway;
    private LegacyRequestTranslator translator;

    @BeforeEach
    void setUp() {
        catalogGateway = mock(VirtualCatalogGateway.class);
        when(catalogGateway.describePublished("orders", null)).thenReturn(ordersCatalog());
        when(catalogGateway.describePublished("customers", null)).thenReturn(customersCatalog());
        when(catalogGateway.describePublished("warehouses", null)).thenReturn(warehousesCatalog());
        translator = new LegacyRequestTranslator(catalogGateway);
    }

    @Test
    void translatesGetIdThroughTheSingleVirtualPrimaryKey() {
        DbQueryGetRequest source = new DbQueryGetRequest();
        source.setTitle("single order");
        source.setModel("orders");
        source.setId(42L);
        source.setFilterDict(Map.of("status", "ACTIVE"));
        DbQueryGetExt ext = new DbQueryGetExt();
        ext.setFields(List.of("id", "name"));
        ext.setSorts(List.of(sort("name", "desc")));
        source.setExt(ext);

        LegacyRequestTranslator.Translation translation = translator.translateGet(source);
        VirtualQueryRequest target = translation.request();

        assertEquals(QueryType.GET, target.getQueryType());
        assertEquals("orders", target.getEntityCode());
        assertEquals(17L, target.getCatalogVersion());
        assertEquals("single order", target.getTraceLabel());
        assertEquals(List.of("id", "name"), target.getFields());
        assertEquals(1, target.getPage().getNumber());
        assertEquals(1, target.getPage().getSize());
        assertEquals(SortDirection.DESC, target.getSorts().get(0).getDirection());

        Map<String, FilterNode> predicates = predicatesByField(target.getFilter());
        assertEquals("ACTIVE", predicates.get("status").getValue());
        assertEquals(FilterOperator.EQ, predicates.get("id").getOperator());
        assertEquals(42L, predicates.get("id").getValue());
    }

    @Test
    void rejectsGetIdShorthandForCompositeVirtualPrimaryKey() {
        VirtualCatalogDescriptor composite = new VirtualCatalogDescriptor(
                "composite_orders",
                3L,
                List.of(field("tenant_id", true), field("id", true)),
                List.of()
        );
        when(catalogGateway.describePublished("composite_orders", null)).thenReturn(composite);
        DbQueryGetRequest source = new DbQueryGetRequest();
        source.setModel("composite_orders");
        source.setId(42L);

        LegacyQueryCompatibilityException exception = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> translator.translateGet(source)
        );

        assertEquals(LegacyRequestTranslator.INVALID_REQUEST, exception.getCode());
    }

    @Test
    void translatesListWithExactTotalNormalizedPageAndPrimaryKeyTieBreaker() {
        DbQueryListRequest source = new DbQueryListRequest();
        source.setModel("orders");
        source.setPage(0);
        source.setPageSize(5000);
        source.getExt().setFields(List.of("id", "name", "name"));
        source.getExt().setSorts(List.of(sort("name", "desc")));

        LegacyRequestTranslator.Translation translation = translator.translateList(source);
        VirtualQueryRequest target = translation.request();

        assertEquals(QueryType.LIST, target.getQueryType());
        assertEquals(Boolean.TRUE, target.getExactTotal());
        assertEquals(1, target.getPage().getNumber());
        assertEquals(1000, target.getPage().getSize());
        assertEquals(List.of("id", "name"), target.getFields());
        assertEquals(List.of("name", "id"), target.getSorts().stream().map(item -> item.getField()).toList());
        assertEquals(List.of(SortDirection.DESC, SortDirection.ASC),
                target.getSorts().stream().map(item -> item.getDirection()).toList());
    }

    @Test
    void expandsAllEnabledFieldsAndOnlyDefaultForwardRelationsWhenFieldsAndRelationsAreEmpty() {
        when(catalogGateway.describePublished("bidirectional_orders", null))
                .thenReturn(bidirectionalOrdersCatalog());
        DbQueryListRequest source = new DbQueryListRequest();
        source.setModel("bidirectional_orders");

        LegacyRequestTranslator.Translation translation = translator.translateList(source);
        VirtualQueryRequest target = translation.request();

        List<String> expectedFields = List.of(
                "id", "customer_id", "name", "parent_id",
                "customer.id", "customer.name", "customer.active"
        );
        assertEquals(expectedFields, target.getFields());
        assertEquals(expectedFields, translation.outputFields());
        assertEquals(List.of("customer"), target.getRelationCodes());
        assertEquals(1, target.getRelations().size());
        assertEquals("customer", target.getRelations().get(0).getKey());
        assertEquals("customer", target.getRelations().get(0).getRelationCode());
        assertEquals(Map.of("customer_id", "id"),
                target.getRelations().get(0).getLocalToRemoteFields());
        assertEquals(RelationResultMode.OBJECT, target.getRelations().get(0).getResultMode());
    }

    @Test
    void expandsAllFieldsForExplicitReverseRelation() {
        when(catalogGateway.describePublished("bidirectional_orders", null))
                .thenReturn(bidirectionalOrdersCatalog());
        DbQueryListRequest source = new DbQueryListRequest();
        source.setModel("bidirectional_orders");
        source.getExt().setRelations(List.of(relation("warehouses", "warehouses", Map.of())));

        LegacyRequestTranslator.Translation translation = translator.translateList(source);
        VirtualQueryRequest target = translation.request();

        assertEquals(List.of(
                "id", "customer_id", "name", "parent_id",
                "warehouses.id", "warehouses.order_id", "warehouses.name"
        ), target.getFields());
        assertEquals(List.of("warehouses"), target.getRelationCodes());
        assertEquals("warehouse_orders", target.getRelations().get(0).getRelationCode());
        assertEquals(Map.of("id", "order_id"), target.getRelations().get(0).getLocalToRemoteFields());
        assertEquals(RelationResultMode.COLLECTION, target.getRelations().get(0).getResultMode());
    }

    @Test
    void keepsExplicitFieldsStrictWhileStillAddingDefaultForwardRelation() {
        when(catalogGateway.describePublished("bidirectional_orders", null))
                .thenReturn(bidirectionalOrdersCatalog());
        DbQueryListRequest source = new DbQueryListRequest();
        source.setModel("bidirectional_orders");
        source.getExt().setFields(List.of("name"));

        LegacyRequestTranslator.Translation translation = translator.translateList(source);

        assertEquals(List.of("name"), translation.request().getFields());
        assertEquals(List.of("name"), translation.outputFields());
        assertEquals(List.of("customer"), translation.request().getRelationCodes());
    }

    @Test
    void expandsDefaultGetFieldsAndForwardRelations() {
        when(catalogGateway.describePublished("bidirectional_orders", null))
                .thenReturn(bidirectionalOrdersCatalog());
        DbQueryGetRequest source = new DbQueryGetRequest();
        source.setModel("bidirectional_orders");

        LegacyRequestTranslator.Translation translation = translator.translateGet(source);

        assertEquals(List.of(
                "id", "customer_id", "name", "parent_id",
                "customer.id", "customer.name", "customer.active"
        ), translation.outputFields());
        assertEquals(List.of("customer"), translation.request().getRelationCodes());
    }

    @Test
    void translatesPlainCountWithoutAccidentallyCreatingAggregateShape() {
        DbQueryCountRequest source = new DbQueryCountRequest();
        source.setTitle("count orders");
        source.setModel("orders");
        source.setPage(2);
        source.setPageSize(25);

        LegacyRequestTranslator.Translation translation = translator.translateCount(source);
        VirtualQueryRequest target = translation.request();

        assertEquals(QueryType.COUNT, target.getQueryType());
        assertTrue(translation.plainCount());
        assertTrue(target.getGroupings().isEmpty());
        assertTrue(target.getAggregates().isEmpty());
        assertEquals(Boolean.TRUE, target.getExactTotal());
        assertEquals(2, target.getPage().getNumber());
        assertEquals(25, target.getPage().getSize());
        assertTrue(target.getRelationCodes().isEmpty());
        assertTrue(target.getRelations().isEmpty());
    }

    @Test
    void translatesAggregateDimensionMetricAliasesHavingAndAliasSorts() {
        DbQueryAggregateRequest source = new DbQueryAggregateRequest();
        source.setModel("orders");
        source.setDimensions(List.of(dimension("region", "region_name")));
        source.setMetrics(List.of(metric("amount", "sum", "total_amount")));
        source.setHaving(Map.of("total_amount", condition("gte", 100)));
        source.setSorts(List.of(sort("region_name", "asc"), sort("total_amount", "desc")));

        LegacyRequestTranslator.Translation translation = translator.translateAggregate(source);
        VirtualQueryRequest target = translation.request();

        assertEquals(QueryType.AGGREGATE, target.getQueryType());
        assertFalse(translation.plainCount());
        assertEquals(List.of("region"), target.getGroupBy());
        assertEquals("region", target.getGroupings().get(0).getField());
        assertEquals("region_name", target.getGroupings().get(0).getAlias());
        assertEquals(AggregateFunction.SUM, target.getAggregates().get(0).getFunction());
        assertEquals("amount", target.getAggregates().get(0).getField());
        assertEquals("total_amount", target.getAggregates().get(0).getAlias());
        assertEquals("total_amount", target.getHaving().getField());
        assertEquals(FilterOperator.GTE, target.getHaving().getOperator());
        assertEquals(100, target.getHaving().getValue());
        assertEquals(List.of("region_name", "total_amount"),
                target.getSorts().stream().map(item -> item.getField()).toList());
        assertTrue(target.getRelationCodes().isEmpty());
        assertTrue(target.getRelations().isEmpty());
    }

    @Test
    void translatesTreeIntoFullyMaterializedVirtualList() {
        DbQueryTreeRequest source = new DbQueryTreeRequest();
        source.setModel("orders");
        source.setFields(List.of("status"));
        DbQueryTreeExt ext = new DbQueryTreeExt();
        ext.setIdField("node_id");
        ext.setParentField("parent_node_id");
        ext.setLabelField("label");
        source.setExt(ext);

        LegacyRequestTranslator.Translation translation = translator.translateTree(source);
        VirtualQueryRequest target = translation.request();

        assertEquals(QueryType.LIST, target.getQueryType());
        assertEquals(List.of("node_id", "parent_node_id", "label", "status"), target.getFields());
        assertEquals(Boolean.TRUE, target.getExactTotal());
        assertEquals(1, target.getPage().getNumber());
        assertEquals(1000, target.getPage().getSize());
        assertEquals(List.of("customer"), target.getRelationCodes());
    }

    @Test
    void expandsDefaultTreeFieldsAndOnlyForwardRelationsWhenSourceFieldsAreEmpty() {
        when(catalogGateway.describePublished("bidirectional_orders", null))
                .thenReturn(bidirectionalOrdersCatalog());
        DbQueryTreeRequest source = new DbQueryTreeRequest();
        source.setModel("bidirectional_orders");

        LegacyRequestTranslator.Translation translation = translator.translateTree(source);

        assertEquals(List.of(
                "id", "customer_id", "name", "parent_id",
                "customer.id", "customer.name", "customer.active"
        ), translation.outputFields());
        assertEquals(translation.outputFields(), translation.request().getFields());
        assertEquals(List.of("customer"), translation.request().getRelationCodes());
    }

    @Test
    void translatesPivotIntoAliasedAggregateRequest() {
        DbQueryPivotRequest source = new DbQueryPivotRequest();
        source.setModel("orders");
        source.setRows(List.of(dimension("region", "row_region")));
        source.setColumns(List.of(dimension("month", "column_month")));
        source.setMetrics(List.of(metric("amount", "sum", "sales")));
        source.setHaving(Map.of("sales", condition("gt", 0)));

        LegacyRequestTranslator.Translation translation = translator.translatePivot(source);
        VirtualQueryRequest target = translation.request();

        assertEquals(QueryType.AGGREGATE, target.getQueryType());
        assertEquals(List.of("region", "month"), target.getGroupBy());
        assertEquals(List.of("row_region", "column_month"),
                target.getGroupings().stream().map(item -> item.getAlias()).toList());
        assertEquals("sales", target.getAggregates().get(0).getAlias());
        assertNotNull(target.getHaving());
        assertEquals("sales", target.getHaving().getField());
        assertEquals(Boolean.TRUE, target.getExactTotal());
        assertEquals(1000, target.getPage().getSize());
        assertTrue(target.getRelationCodes().isEmpty());
        assertTrue(target.getRelations().isEmpty());
    }

    @Test
    void resolvesPublishedRelationByModelAndKeepsArbitraryAliasAndScopedFilter() {
        DbQueryRelation relation = relation("buyer", "customers", Map.of());
        relation.setFilter(Map.of("active", true));
        DbQueryListRequest source = new DbQueryListRequest();
        source.setModel("orders");
        source.getExt().setRelations(List.of(relation));
        source.getExt().setFields(List.of("id", "buyer.name"));

        VirtualQueryRequest target = translator.translateList(source).request();

        assertEquals(List.of("buyer"), target.getRelationCodes());
        assertEquals(1, target.getRelations().size());
        assertEquals("buyer", target.getRelations().get(0).getKey());
        assertEquals("customer", target.getRelations().get(0).getRelationCode());
        assertEquals("customers", target.getRelations().get(0).getTargetEntityCode());
        assertEquals(Map.of("customer_id", "id"), target.getRelations().get(0).getLocalToRemoteFields());
        assertEquals(RelationResultMode.OBJECT, target.getRelations().get(0).getResultMode());
        assertEquals("active", target.getRelations().get(0).getFilter().getField());
        assertEquals(Boolean.TRUE, target.getRelations().get(0).getFilter().getValue());
        assertNull(target.getFilter(), "relation.filter 不能混入主查询 WHERE AST");
    }

    @Test
    void requiresRelationKeyAndModelAndRejectsUnsafeAliases() {
        assertRelationError(
                relation(null, "customers", Map.of()),
                LegacyRequestTranslator.INVALID_RELATION
        );
        assertRelationError(
                relation("buyer", null, Map.of()),
                LegacyRequestTranslator.INVALID_RELATION
        );
        assertRelationError(
                relation("buyer.info", "customers", Map.of()),
                LegacyRequestTranslator.INVALID_RELATION
        );
        assertRelationError(
                relation("name", "customers", Map.of()),
                LegacyRequestTranslator.INVALID_RELATION
        );

        DbQueryListRequest duplicate = new DbQueryListRequest();
        duplicate.setModel("orders");
        duplicate.getExt().setRelations(List.of(
                relation("buyer", "customers", Map.of()),
                relation("buyer", "warehouses", Map.of("customer_id", "id"))
        ));
        LegacyQueryCompatibilityException duplicateException = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> translator.translateList(duplicate)
        );
        assertEquals(LegacyRequestTranslator.INVALID_RELATION, duplicateException.getCode());
    }

    @Test
    void createsAdHocRelationWhenOnIsProvidedAndInfersResultModeFromRemotePrimaryKey() {
        DbQueryListRequest source = new DbQueryListRequest();
        source.setModel("orders");
        source.getExt().setRelations(List.of(
                relation("warehouse", "warehouses", Map.of("customer_id", "id")),
                relation("warehouse_orders", "warehouses", Map.of("id", "order_id"))
        ));
        source.getExt().setFields(List.of("id", "warehouse.name", "warehouse_orders.name"));

        VirtualQueryRequest target = translator.translateList(source).request();

        assertEquals(List.of("warehouse", "warehouse_orders"), target.getRelationCodes());
        assertNull(target.getRelations().get(0).getRelationCode());
        assertEquals(Map.of("customer_id", "id"), target.getRelations().get(0).getLocalToRemoteFields());
        assertEquals(RelationResultMode.OBJECT, target.getRelations().get(0).getResultMode());
        assertEquals(RelationResultMode.COLLECTION, target.getRelations().get(1).getResultMode());

        assertRelationError(
                relation("warehouse", "warehouses", Map.of()),
                LegacyRequestTranslator.INVALID_RELATION
        );
    }

    @Test
    void requiresOnToDisambiguatePublishedRelationsForTheSameModel() {
        when(catalogGateway.describePublished("ambiguous_orders", null)).thenReturn(ambiguousOrdersCatalog());

        assertRelationError(
                "ambiguous_orders",
                relation("buyer", "customers", Map.of()),
                LegacyRequestTranslator.INVALID_RELATION
        );

        DbQueryListRequest source = new DbQueryListRequest();
        source.setModel("ambiguous_orders");
        source.getExt().setRelations(List.of(relation("buyer", "customers", Map.of("name", "name"))));
        source.getExt().setFields(List.of("id", "buyer.name"));

        VirtualQueryRequest target = translator.translateList(source).request();

        assertEquals("customer_by_name", target.getRelations().get(0).getRelationCode());
        assertEquals(Map.of("name", "name"), target.getRelations().get(0).getLocalToRemoteFields());
    }

    @Test
    void collapsesEquivalentForwardAndReverseDefinitionsAndRejectsMismatchedOn() {
        when(catalogGateway.describePublished("equivalent_orders", null)).thenReturn(equivalentOrdersCatalog());

        DbQueryListRequest source = new DbQueryListRequest();
        source.setModel("equivalent_orders");
        source.getExt().setRelations(List.of(relation("buyer", "customers", Map.of())));
        source.getExt().setFields(List.of("id", "buyer.name"));

        VirtualQueryRequest target = translator.translateList(source).request();

        assertEquals("customer", target.getRelations().get(0).getRelationCode());
        assertRelationError(
                "equivalent_orders",
                relation("buyer", "customers", Map.of("name", "name")),
                LegacyRequestTranslator.RELATION_METADATA_MISMATCH
        );
    }

    @Test
    void validatesOnFieldsAndProjectedRemoteFieldsAgainstPublishedCatalogs() {
        assertRelationError(
                relation("warehouse", "warehouses", Map.of("wrong_local", "id")),
                LegacyRequestTranslator.INVALID_FIELD
        );
        assertRelationError(
                relation("warehouse", "warehouses", Map.of("customer_id", "wrong_remote")),
                LegacyRequestTranslator.INVALID_FIELD
        );

        DbQueryListRequest source = new DbQueryListRequest();
        source.setModel("orders");
        source.getExt().setRelations(List.of(relation("buyer", "customers", Map.of())));
        source.getExt().setFields(List.of("buyer.unknown"));

        LegacyQueryCompatibilityException exception = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> translator.translateList(source)
        );
        assertEquals(LegacyRequestTranslator.INVALID_FIELD, exception.getCode());
    }

    @Test
    void rejectsHavingAndAggregateSortOutsidePublishedAliases() {
        DbQueryAggregateRequest invalidHaving = new DbQueryAggregateRequest();
        invalidHaving.setModel("orders");
        invalidHaving.setMetrics(List.of(metric("amount", "sum", "sales")));
        invalidHaving.setHaving(Map.of("amount", condition("gt", 0)));

        LegacyQueryCompatibilityException havingException = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> translator.translateAggregate(invalidHaving)
        );
        assertEquals(LegacyRequestTranslator.INVALID_FIELD, havingException.getCode());

        DbQueryAggregateRequest invalidSort = new DbQueryAggregateRequest();
        invalidSort.setModel("orders");
        invalidSort.setMetrics(List.of(metric("amount", "sum", "sales")));
        invalidSort.setSorts(List.of(sort("amount", "desc")));

        LegacyQueryCompatibilityException sortException = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> translator.translateAggregate(invalidSort)
        );
        assertEquals(LegacyRequestTranslator.INVALID_FIELD, sortException.getCode());
    }

    private void assertRelationError(DbQueryRelation relation, String expectedCode) {
        assertRelationError("orders", relation, expectedCode);
    }

    private void assertRelationError(String model, DbQueryRelation relation, String expectedCode) {
        DbQueryListRequest source = new DbQueryListRequest();
        source.setModel(model);
        source.getExt().setRelations(List.of(relation));

        LegacyQueryCompatibilityException exception = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> translator.translateList(source)
        );
        assertEquals(expectedCode, exception.getCode());
    }

    private Map<String, FilterNode> predicatesByField(FilterNode root) {
        Map<String, FilterNode> result = new LinkedHashMap<>();
        collectPredicates(root, result);
        return result;
    }

    private void collectPredicates(FilterNode node, Map<String, FilterNode> target) {
        if (node == null) {
            return;
        }
        if (node.getType() == FilterType.PREDICATE) {
            target.put(node.getField(), node);
        }
        if (node.getChildren() != null) {
            node.getChildren().forEach(child -> collectPredicates(child, target));
        }
    }

    private VirtualCatalogDescriptor ordersCatalog() {
        List<VirtualCatalogDescriptor.Field> fields = new ArrayList<>(List.of(
                field("id", true),
                field("customer_id", false),
                field("name", false),
                field("status", false),
                field("amount", false),
                field("region", false),
                field("month", false),
                field("node_id", false),
                field("parent_node_id", false),
                field("label", false),
                field("parent_id", false)
        ));
        VirtualCatalogDescriptor.Relation relation = new VirtualCatalogDescriptor.Relation(
                "customer",
                "customers",
                Map.of("customer_id", "id")
        );
        return new VirtualCatalogDescriptor("orders", 17L, fields, List.of("customer"), List.of(relation));
    }

    private VirtualCatalogDescriptor customersCatalog() {
        return new VirtualCatalogDescriptor(
                "customers",
                9L,
                List.of(
                        field("id", true),
                        field("name", false),
                        field("active", false),
                        disabledField("private_note")
                ),
                List.of()
        );
    }

    private VirtualCatalogDescriptor warehousesCatalog() {
        return new VirtualCatalogDescriptor(
                "warehouses",
                5L,
                List.of(
                        field("id", true),
                        field("order_id", false),
                        field("name", false)
                ),
                List.of()
        );
    }

    private VirtualCatalogDescriptor bidirectionalOrdersCatalog() {
        List<VirtualCatalogDescriptor.Field> fields = List.of(
                field("id", true),
                field("customer_id", false),
                field("name", false),
                field("parent_id", false),
                disabledField("internal_note")
        );
        List<VirtualCatalogDescriptor.Relation> relations = List.of(
                new VirtualCatalogDescriptor.Relation(
                        "customer",
                        "customers",
                        Map.of("customer_id", "id"),
                        RelationResultMode.OBJECT,
                        false
                ),
                new VirtualCatalogDescriptor.Relation(
                        "warehouse_orders",
                        "warehouses",
                        Map.of("id", "order_id"),
                        RelationResultMode.COLLECTION,
                        true
                )
        );
        return new VirtualCatalogDescriptor(
                "bidirectional_orders",
                20L,
                fields,
                relations.stream().map(VirtualCatalogDescriptor.Relation::code).toList(),
                relations
        );
    }

    private VirtualCatalogDescriptor ambiguousOrdersCatalog() {
        VirtualCatalogDescriptor base = ordersCatalog();
        List<VirtualCatalogDescriptor.Relation> relations = List.of(
                new VirtualCatalogDescriptor.Relation(
                        "customer_by_id",
                        "customers",
                        Map.of("customer_id", "id")
                ),
                new VirtualCatalogDescriptor.Relation(
                        "customer_by_name",
                        "customers",
                        Map.of("name", "name")
                )
        );
        return new VirtualCatalogDescriptor(
                "ambiguous_orders",
                18L,
                base.fields(),
                relations.stream().map(VirtualCatalogDescriptor.Relation::code).toList(),
                relations
        );
    }

    private VirtualCatalogDescriptor equivalentOrdersCatalog() {
        VirtualCatalogDescriptor base = ordersCatalog();
        List<VirtualCatalogDescriptor.Relation> relations = List.of(
                new VirtualCatalogDescriptor.Relation(
                        "customer", "customers", Map.of("customer_id", "id"), RelationResultMode.OBJECT),
                new VirtualCatalogDescriptor.Relation(
                        "orders", "customers", Map.of("customer_id", "id"), RelationResultMode.OBJECT, true)
        );
        return new VirtualCatalogDescriptor(
                "equivalent_orders",
                19L,
                base.fields(),
                relations.stream().map(VirtualCatalogDescriptor.Relation::code).toList(),
                relations
        );
    }

    private VirtualCatalogDescriptor.Field field(String code, boolean primaryKey) {
        return new VirtualCatalogDescriptor.Field(code, primaryKey, true);
    }

    private VirtualCatalogDescriptor.Field disabledField(String code) {
        return new VirtualCatalogDescriptor.Field(code, false, false);
    }

    private DbQueryRelation relation(String key, String model, Map<String, String> on) {
        DbQueryRelation relation = new DbQueryRelation();
        relation.setKey(key);
        relation.setModel(model);
        relation.setType("left");
        relation.setOn(on == null ? new LinkedHashMap<>() : new LinkedHashMap<>(on));
        return relation;
    }

    private DbQueryCountDimension dimension(String field, String alias) {
        DbQueryCountDimension dimension = new DbQueryCountDimension();
        dimension.setField(field);
        dimension.setAlias(alias);
        return dimension;
    }

    private DbQueryCountMetric metric(String field, String function, String alias) {
        DbQueryCountMetric metric = new DbQueryCountMetric();
        metric.setField(field);
        metric.setFunc(function);
        metric.setAlias(alias);
        return metric;
    }

    private DbQueryFilterCondition condition(String operator, Object value) {
        DbQueryFilterCondition condition = new DbQueryFilterCondition();
        condition.setOp(operator);
        condition.setValue(value);
        return condition;
    }

    private DbQuerySort sort(String field, String order) {
        DbQuerySort sort = new DbQuerySort();
        sort.setField(field);
        sort.setOrder(order);
        return sort;
    }
}
