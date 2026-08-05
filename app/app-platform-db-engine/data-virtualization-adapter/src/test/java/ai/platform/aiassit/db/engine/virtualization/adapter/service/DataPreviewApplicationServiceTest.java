package ai.platform.aiassit.db.engine.virtualization.adapter.service;

import ai.platform.aiassit.data.virtualization.api.VirtualCatalogGateway;
import ai.platform.aiassit.data.virtualization.api.VirtualQueryGateway;
import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCatalogDescriptor;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualExplainResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.AggregateFunction;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RelationResultMode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.SortDirection;
import ai.platform.aiassit.data.virtualization.api.exception.VirtualDataRuntimeException;
import ai.platform.aiassit.db.engine.api.constant.DataPreviewErrorCode;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryRequest;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryResponse;
import org.arthena.framework.common.context.SystemContext;
import org.athena.framework.security.api.model.MutableUserContext;
import org.athena.framework.security.api.model.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataPreviewApplicationServiceTest {

    private StubVirtualCatalogGateway catalogGateway;
    private StubVirtualQueryGateway queryGateway;

    @BeforeEach
    void setUp() {
        catalogGateway = new StubVirtualCatalogGateway();
        queryGateway = new StubVirtualQueryGateway();
        catalogGateway.stub("orders", 17L, ordersCatalog());
        MutableUserContext userContext = new MutableUserContext();
        userContext.setSubject(new Subject(7L, "preview-user", "default", "USER"));
        SystemContext.setUserContext(userContext);
    }

    @AfterEach
    void tearDown() {
        SystemContext.clearUserContext();
    }

    @Test
    void rejectsUnauthenticatedRequestBeforeCatalogLookup() {
        SystemContext.clearUserContext();
        DataPreviewApplicationService service = service(allowRequestedFields());

        assertThatThrownBy(() -> service.query(baseRequest()))
                .isInstanceOfSatisfying(VirtualDataRuntimeException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(DataPreviewErrorCode.AUTH_REQUIRED));
        assertThat(catalogGateway.callCount).isZero();
        assertThat(queryGateway.callCount).isZero();
    }

    @Test
    void translatesDimensionPreviewToCappedListAndMergesEnforcedRowFilter() {
        DataPreviewAccessPolicy policy = request -> new DataPreviewAccessPolicy.AccessDecision(
                request.requestedFields(),
                predicate("tenant_id", FilterOperator.EQ, 7L)
        );
        DataPreviewApplicationService service = service(policy);
        queryGateway.response = response(
                2L,
                row("id", 1L, "name", "first"),
                row("id", 2L, "name", "second")
        );

        DataPreviewQueryRequest request = baseRequest();
        request.setLimit(500);
        request.setDimensions(List.of(
                dimension("id", "order_id", "订单编号"),
                dimension("name", null, "订单名称")
        ));
        request.setFilters(List.of(filter("status", "EQ", "PAID")));

        DataPreviewQueryResponse result = service.query(request);

        VirtualQueryRequest target = queryGateway.lastRequest;
        assertThat(target.getQueryType()).isEqualTo(QueryType.LIST);
        assertThat(target.getCatalogVersion()).isEqualTo(17L);
        assertThat(target.getFields()).containsExactly("id", "name");
        assertThat(target.getPage().getNumber()).isEqualTo(1);
        assertThat(target.getPage().getSize()).isEqualTo(DataPreviewApplicationService.MAX_PREVIEW_ROWS);
        assertThat(target.getExactTotal()).isFalse();
        assertThat(target.getHints().getMaxPhysicalTasks()).isEqualTo(DataPreviewApplicationService.MAX_PHYSICAL_TASKS);
        assertThat(target.getHints().getMaxScanRows()).isEqualTo(DataPreviewApplicationService.MAX_SCAN_ROWS);
        assertThat(target.getHints().getTimeoutMs()).isEqualTo(DataPreviewApplicationService.TIMEOUT_MS);
        assertThat(predicatesByField(target.getFilter())).containsKeys("status", "tenant_id");

        assertThat(result.getModel()).isEqualTo("orders");
        assertThat(result.getSourceRevision()).isEqualTo("virtual-model/v17");
        assertThat(result.getQueryType()).isEqualTo("LIST");
        assertThat(result.getColumns()).extracting(DataPreviewQueryResponse.Column::getKey)
                .containsExactly("order_id", "name");
        assertThat(result.getRecords().get(0)).containsEntry("order_id", 1L).containsEntry("name", "first");
        assertThat(result.getTruncated()).isFalse();
    }

    @Test
    void keepsUnknownListDimensionAsNullAndRemovesItFromQueryProjection() {
        DataPreviewApplicationService service = service(allowRequestedFields());
        queryGateway.response = response(1L, row("id", 1L, "physical_secret", "hidden"));

        DataPreviewQueryRequest request = baseRequest();
        request.setDimensions(List.of(
                dimension("id", null, "编号"),
                dimension("consignee", null, "收件人")
        ));
        request.setSorts(List.of(sort("consignee", "DESC")));

        DataPreviewQueryResponse result = service.query(request);

        assertThat(queryGateway.lastRequest.getFields()).containsExactly("id");
        assertThat(queryGateway.lastRequest.getSorts()).isEmpty();
        assertThat(result.getColumns()).extracting(DataPreviewQueryResponse.Column::getKey)
                .containsExactly("id", "consignee");
        assertThat(result.getRecords()).singleElement().satisfies(record -> {
            assertThat(record).containsEntry("id", 1L);
            assertThat(record.keySet()).contains("consignee");
            assertThat(record.get("consignee")).isNull();
            assertThat(record.keySet()).doesNotContain("physical_secret");
        });
    }

    @Test
    void usesSafeFallbackProjectionWhenAllListDimensionsAreUnknown() {
        DataPreviewApplicationService service = service(allowRequestedFields());
        queryGateway.response = response(1L, row("id", 1L));

        DataPreviewQueryRequest request = baseRequest();
        request.setDimensions(List.of(dimension("consignee", null, "收件人")));

        DataPreviewQueryResponse result = service.query(request);

        assertThat(queryGateway.lastRequest.getFields()).containsExactly("id");
        assertThat(queryGateway.lastRequest.getFields()).doesNotContain("consignee");
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).keySet()).contains("consignee");
        assertThat(result.getRecords().get(0).get("consignee")).isNull();
    }

    @Test
    void keepsUnknownOutputOptionalButRejectsUnknownFilter() {
        DataPreviewApplicationService service = service(allowRequestedFields());
        DataPreviewQueryRequest request = baseRequest();
        request.setDimensions(List.of(dimension("consignee", null, "收件人")));
        request.setFilters(List.of(filter("consignee", "EQ", "Alice")));

        assertThatThrownBy(() -> service.query(request))
                .isInstanceOfSatisfying(VirtualDataRuntimeException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(DataPreviewErrorCode.FIELD_NOT_FOUND));
        assertThat(queryGateway.callCount).isZero();
    }

    @Test
    void translatesMeasuresDimensionsTimeRangeAndSortToAggregatePreview() {
        DataPreviewApplicationService service = service(allowRequestedFields());
        queryGateway.response = response(1L, row("region", "CN", "sales", 100L));

        DataPreviewQueryRequest request = baseRequest();
        request.setDimensions(List.of(dimension("region", null, "区域")));
        request.setMeasures(List.of(measure("amount", "SUM", "sales", "销售额")));
        request.setFilters(List.of(filter("status", "EQ", "PAID")));
        DataPreviewQueryRequest.TimeRange timeRange = new DataPreviewQueryRequest.TimeRange();
        timeRange.setField("paid_at");
        timeRange.setPreset("LAST_6_MONTHS");
        request.setTimeRange(timeRange);
        request.setSorts(List.of(sort("sales", "DESC")));

        DataPreviewQueryResponse result = service.query(request);

        VirtualQueryRequest target = queryGateway.lastRequest;
        assertThat(target.getQueryType()).isEqualTo(QueryType.AGGREGATE);
        assertThat(target.getFields()).isEmpty();
        assertThat(target.getGroupings()).hasSize(1);
        assertThat(target.getGroupings().get(0).getField()).isEqualTo("region");
        assertThat(target.getGroupings().get(0).getAlias()).isEqualTo("region");
        assertThat(target.getAggregates()).hasSize(1);
        assertThat(target.getAggregates().get(0).getField()).isEqualTo("amount");
        assertThat(target.getAggregates().get(0).getFunction()).isEqualTo(AggregateFunction.SUM);
        assertThat(target.getAggregates().get(0).getAlias()).isEqualTo("sales");
        assertThat(target.getSorts()).hasSize(1);
        assertThat(target.getSorts().get(0).getField()).isEqualTo("sales");
        assertThat(target.getSorts().get(0).getDirection()).isEqualTo(SortDirection.DESC);
        assertThat(predicatesByField(target.getFilter())).containsKeys("status", "paid_at");
        assertThat(predicatesByField(target.getFilter()).get("paid_at"))
                .extracting(FilterNode::getOperator)
                .containsExactlyInAnyOrder(FilterOperator.GTE, FilterOperator.LT);

        assertThat(result.getQueryType()).isEqualTo("AGGREGATE");
        assertThat(result.getColumns()).extracting(DataPreviewQueryResponse.Column::getKey)
                .containsExactly("region", "sales");
        assertThat(result.getRecords()).containsExactly(row("region", "CN", "sales", 100L));
    }

    @Test
    void keepsUnknownAggregateAsNullAndRemovesItFromExecutionAggregates() {
        DataPreviewApplicationService service = service(allowRequestedFields());
        queryGateway.response = response(1L, row("region", "CN", "sales", 100L));

        DataPreviewQueryRequest request = baseRequest();
        request.setDimensions(List.of(dimension("region", null, "区域")));
        request.setMeasures(List.of(
                measure("amount", "SUM", "sales", "销售额"),
                measure("missing_amount", "COUNT", "missing_count", "缺失金额")
        ));

        DataPreviewQueryResponse result = service.query(request);

        assertThat(queryGateway.lastRequest.getAggregates()).extracting(aggregate -> aggregate.getField())
                .containsExactly("amount");
        assertThat(result.getColumns()).extracting(DataPreviewQueryResponse.Column::getKey)
                .containsExactly("region", "sales", "missing_count");
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0)).containsEntry("region", "CN").containsEntry("sales", 100L);
        assertThat(result.getRecords().get(0).get("missing_count")).isNull();
    }

    @Test
    void enrichesPreviewColumnsWithCatalogNameAndLogicalType() {
        catalogGateway.stub("orders", 17L, new VirtualCatalogDescriptor(
                "orders",
                17L,
                List.of(new VirtualCatalogDescriptor.Field(
                        "is_default", "是否默认", LogicalType.BOOLEAN, false, true)),
                List.of()
        ));
        queryGateway.response = response(2L, row("is_default", 1), row("is_default", 0));

        DataPreviewQueryRequest request = baseRequest();
        request.setDimensions(List.of(dimension("is_default", null, null)));

        DataPreviewQueryResponse result = service(allowRequestedFields()).query(request);

        assertThat(result.getColumns()).singleElement().satisfies(column -> {
            assertThat(column.getLabel()).isEqualTo("是否默认");
            assertThat(column.getDataType()).isEqualTo("boolean");
        });
    }

    @Test
    void infersOnlyPublishedRelationFromExplicitRelationField() {
        VirtualCatalogDescriptor.Relation customer = new VirtualCatalogDescriptor.Relation(
                "customer",
                "customers",
                Map.of("customer_id", "id"),
                RelationResultMode.OBJECT
        );
        catalogGateway.stub("orders", 17L, new VirtualCatalogDescriptor(
                "orders",
                17L,
                ordersCatalog().fields(),
                List.of("customer"),
                List.of(customer)
        ));
        catalogGateway.stub("customers", null, new VirtualCatalogDescriptor(
                "customers",
                3L,
                List.of(field("id"), field("name")),
                List.of()
        ));
        queryGateway.response = response(1L, row("customer.name", "Alice"));
        DataPreviewApplicationService service = service(allowRequestedFields());
        DataPreviewQueryRequest request = baseRequest();
        request.setDimensions(List.of(dimension("customer.name", null, "客户")));

        DataPreviewQueryResponse result = service.query(request);

        assertThat(queryGateway.lastRequest.getRelationCodes()).containsExactly("customer");
        assertThat(queryGateway.lastRequest.getFields()).containsExactly("customer.name");
        assertThat(result.getColumns().get(0).getKey()).isEqualTo("customer_name");
        assertThat(result.getRecords().get(0)).containsEntry("customer_name", "Alice");
    }

    @Test
    void rejectsFieldOutsideAccessDecisionBeforeQueryExecution() {
        DataPreviewAccessPolicy policy = request -> new DataPreviewAccessPolicy.AccessDecision(Set.of("id"), null);
        DataPreviewApplicationService service = service(policy);
        DataPreviewQueryRequest request = baseRequest();
        request.setDimensions(List.of(dimension("name", null, null)));

        assertThatThrownBy(() -> service.query(request))
                .isInstanceOfSatisfying(VirtualDataRuntimeException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(DataPreviewErrorCode.FIELD_FORBIDDEN));
        assertThat(queryGateway.callCount).isZero();
    }

    @Test
    void rejectsConflictingKnowledgeRevisionBeforeCatalogLookup() {
        DataPreviewApplicationService service = service(allowRequestedFields());
        DataPreviewQueryRequest request = baseRequest();
        request.setCatalogVersion(17L);
        request.setSourceRevision("virtual-model/v18");
        request.setDimensions(List.of(dimension("id", null, null)));

        assertThatThrownBy(() -> service.query(request))
                .isInstanceOfSatisfying(VirtualDataRuntimeException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(DataPreviewErrorCode.SOURCE_REVISION_CONFLICT));
        assertThat(catalogGateway.callCount).isZero();
        assertThat(queryGateway.callCount).isZero();
    }

    @Test
    void rejectsRequestsWithoutAuditableSourceRevision() {
        DataPreviewApplicationService service = service(allowRequestedFields());
        DataPreviewQueryRequest request = new DataPreviewQueryRequest();
        request.setModel("orders");
        request.setDimensions(List.of(dimension("id", null, null)));

        assertThatThrownBy(() -> service.query(request))
                .isInstanceOfSatisfying(VirtualDataRuntimeException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(DataPreviewErrorCode.SOURCE_REVISION_INVALID));
        assertThat(catalogGateway.callCount).isZero();
        assertThat(queryGateway.callCount).isZero();
    }

    @Test
    void projectsAggregateColumnsAndCapsUnexpectedGatewayRows() {
        DataPreviewApplicationService service = service(allowRequestedFields());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < 105; index++) {
            rows.add(row("region", "CN", "sales", index, "physical_secret", "hidden"));
        }
        queryGateway.response = response(105L, rows.toArray(Map[]::new));

        DataPreviewQueryRequest request = baseRequest();
        request.setDimensions(List.of(dimension("region", null, null)));
        request.setMeasures(List.of(measure("amount", "SUM", "sales", null)));

        DataPreviewQueryResponse result = service.query(request);

        assertThat(result.getRecords()).hasSize(DataPreviewApplicationService.MAX_PREVIEW_ROWS);
        assertThat(result.getRecords().get(0)).containsOnlyKeys("region", "sales");
        assertThat(result.getTruncated()).isTrue();
    }

    @Test
    void rejectsCatalogResponseThatDoesNotHonorRequestedRevision() {
        catalogGateway.stub("orders", 17L, new VirtualCatalogDescriptor(
                "orders",
                18L,
                ordersCatalog().fields(),
                List.of()
        ));
        DataPreviewApplicationService service = service(allowRequestedFields());

        assertThatThrownBy(() -> service.query(baseRequest()))
                .isInstanceOfSatisfying(VirtualDataRuntimeException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(DataPreviewErrorCode.SOURCE_REVISION_CONFLICT));
        assertThat(queryGateway.callCount).isZero();
    }

    private DataPreviewApplicationService service(DataPreviewAccessPolicy policy) {
        return new DataPreviewApplicationService(catalogGateway, queryGateway, policy);
    }

    private DataPreviewAccessPolicy allowRequestedFields() {
        return request -> new DataPreviewAccessPolicy.AccessDecision(request.requestedFields(), null);
    }

    private DataPreviewQueryRequest baseRequest() {
        DataPreviewQueryRequest request = new DataPreviewQueryRequest();
        request.setModel("orders");
        request.setSourceRevision("virtual-model/v17");
        return request;
    }

    private DataPreviewQueryRequest.Dimension dimension(String field, String alias, String label) {
        DataPreviewQueryRequest.Dimension dimension = new DataPreviewQueryRequest.Dimension();
        dimension.setField(field);
        dimension.setAlias(alias);
        dimension.setLabel(label);
        return dimension;
    }

    private DataPreviewQueryRequest.Measure measure(String field, String aggregation, String alias, String label) {
        DataPreviewQueryRequest.Measure measure = new DataPreviewQueryRequest.Measure();
        measure.setField(field);
        measure.setAggregation(aggregation);
        measure.setAlias(alias);
        measure.setLabel(label);
        return measure;
    }

    private DataPreviewQueryRequest.Filter filter(String field, String operator, Object value) {
        DataPreviewQueryRequest.Filter filter = new DataPreviewQueryRequest.Filter();
        filter.setField(field);
        filter.setOperator(operator);
        filter.setValue(value);
        return filter;
    }

    private DataPreviewQueryRequest.Sort sort(String field, String direction) {
        DataPreviewQueryRequest.Sort sort = new DataPreviewQueryRequest.Sort();
        sort.setField(field);
        sort.setDirection(direction);
        return sort;
    }

    private VirtualCatalogDescriptor ordersCatalog() {
        return new VirtualCatalogDescriptor(
                "orders",
                17L,
                List.of(
                        field("id"),
                        field("name"),
                        field("status"),
                        field("tenant_id"),
                        field("region"),
                        field("amount"),
                        field("paid_at"),
                        field("customer_id")
                ),
                List.of()
        );
    }

    private VirtualCatalogDescriptor.Field field(String code) {
        return new VirtualCatalogDescriptor.Field(code, "id".equals(code), true);
    }

    @SafeVarargs
    private final VirtualQueryResponse response(long total, Map<String, Object>... rows) {
        VirtualQueryResponse response = new VirtualQueryResponse();
        response.setRequestId("request-1");
        response.setPlanId("plan-1");
        response.setCatalogVersion(17L);
        response.setRecords(new ArrayList<>(List.of(rows)));
        response.setTotal(total);
        response.setPhysicalTaskCount(1);
        response.setExecutionMs(12L);
        return response;
    }

    private FilterNode predicate(String field, FilterOperator operator, Object value) {
        FilterNode node = new FilterNode();
        node.setType(FilterType.PREDICATE);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(value);
        return node;
    }

    private Map<String, List<FilterNode>> predicatesByField(FilterNode root) {
        List<FilterNode> predicates = new ArrayList<>();
        collectPredicates(root, predicates);
        return predicates.stream().collect(Collectors.groupingBy(
                FilterNode::getField,
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

    private void collectPredicates(FilterNode node, List<FilterNode> target) {
        if (node == null) {
            return;
        }
        if (node.getType() == FilterType.PREDICATE) {
            target.add(node);
            return;
        }
        if (node.getChildren() != null) {
            node.getChildren().forEach(child -> collectPredicates(child, target));
        }
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }

    private static final class StubVirtualCatalogGateway implements VirtualCatalogGateway {

        private final Map<String, VirtualCatalogDescriptor> descriptors = new HashMap<>();
        private int callCount;

        private void stub(String entityCode, Long catalogVersion, VirtualCatalogDescriptor descriptor) {
            descriptors.put(key(entityCode, catalogVersion), descriptor);
        }

        @Override
        public VirtualCatalogDescriptor describePublished(String entityCode, Long catalogVersion) {
            callCount++;
            return descriptors.get(key(entityCode, catalogVersion));
        }

        private static String key(String entityCode, Long catalogVersion) {
            return entityCode + "@" + catalogVersion;
        }
    }

    private static final class StubVirtualQueryGateway implements VirtualQueryGateway {

        private VirtualQueryResponse response;
        private VirtualQueryRequest lastRequest;
        private int callCount;

        @Override
        public VirtualQueryResponse query(VirtualQueryRequest request) {
            callCount++;
            lastRequest = request;
            return response;
        }

        @Override
        public VirtualExplainResponse explain(VirtualQueryRequest request) {
            throw new UnsupportedOperationException("explain is not used by data-preview tests");
        }
    }
}
