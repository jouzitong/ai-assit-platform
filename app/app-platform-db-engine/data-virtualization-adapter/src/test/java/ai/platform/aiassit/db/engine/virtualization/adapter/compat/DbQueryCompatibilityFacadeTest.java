package ai.platform.aiassit.db.engine.virtualization.adapter.compat;

import ai.platform.aiassit.data.virtualization.api.VirtualCatalogGateway;
import ai.platform.aiassit.data.virtualization.api.VirtualQueryGateway;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCatalogDescriptor;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountDimension;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountMetric;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryListRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbQueryCompatibilityFacadeTest {

    private VirtualQueryGateway queryGateway;
    private DbQueryCompatibilityFacade facade;

    @BeforeEach
    void setUp() {
        queryGateway = mock(VirtualQueryGateway.class);
        VirtualCatalogGateway catalogGateway = mock(VirtualCatalogGateway.class);
        when(catalogGateway.describePublished("orders", null)).thenReturn(catalog());
        facade = new DbQueryCompatibilityFacade(queryGateway, catalogGateway);
    }

    @Test
    void routesAllSixLegacyOperationsThroughVirtualQueryGateway() {
        when(queryGateway.query(any())).thenReturn(
                response(1L, row("id", 1L, "name", "order")),
                response(3L, row("id", 1L, "name", "order")),
                response(3L),
                response(1L, row("count", 3L)),
                response(1L, row("id", 1L, "parent_id", 0L, "name", "root")),
                response(1L, row("row_region", "CN", "column_month", "07", "sales", 100))
        );

        facade.queryGet(getRequest());
        facade.queryList(listRequest());
        facade.queryCount(countRequest());
        facade.queryAggregate(aggregateRequest());
        facade.queryTree(treeRequest());
        facade.queryPivot(pivotRequest());

        ArgumentCaptor<VirtualQueryRequest> captor = ArgumentCaptor.forClass(VirtualQueryRequest.class);
        verify(queryGateway, times(6)).query(captor.capture());
        verify(queryGateway, never()).explain(any());
        assertEquals(
                List.of(QueryType.GET, QueryType.LIST, QueryType.COUNT, QueryType.AGGREGATE,
                        QueryType.LIST, QueryType.AGGREGATE),
                captor.getAllValues().stream().map(VirtualQueryRequest::getQueryType).toList()
        );
        assertEquals(Boolean.TRUE, captor.getAllValues().get(1).getExactTotal());
        assertEquals(Boolean.TRUE, captor.getAllValues().get(4).getExactTotal());
        assertEquals(Boolean.TRUE, captor.getAllValues().get(5).getExactTotal());
    }

    @Test
    void rejectsTreePostProcessingWhenVirtualResultIsNotFullyMaterialized() {
        when(queryGateway.query(any())).thenReturn(
                response(2L, row("id", 1L, "parent_id", 0L, "name", "root"))
        );

        LegacyQueryCompatibilityException exception = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> facade.queryTree(treeRequest())
        );

        assertEquals("PLAN_EXACTNESS_UNPROVABLE", exception.getCode());
        verify(queryGateway).query(any());
    }

    @Test
    void rejectsPivotPostProcessingWhenVirtualResultIsNotFullyMaterialized() {
        when(queryGateway.query(any())).thenReturn(
                response(2L, row("row_region", "CN", "column_month", "07", "sales", 100))
        );

        LegacyQueryCompatibilityException exception = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> facade.queryPivot(pivotRequest())
        );

        assertEquals("PLAN_EXACTNESS_UNPROVABLE", exception.getCode());
        verify(queryGateway).query(any());
    }

    private DbQueryGetRequest getRequest() {
        DbQueryGetRequest request = new DbQueryGetRequest();
        request.setModel("orders");
        request.setId(1L);
        return request;
    }

    private DbQueryListRequest listRequest() {
        DbQueryListRequest request = new DbQueryListRequest();
        request.setModel("orders");
        return request;
    }

    private DbQueryCountRequest countRequest() {
        DbQueryCountRequest request = new DbQueryCountRequest();
        request.setModel("orders");
        return request;
    }

    private DbQueryAggregateRequest aggregateRequest() {
        DbQueryAggregateRequest request = new DbQueryAggregateRequest();
        request.setModel("orders");
        return request;
    }

    private DbQueryTreeRequest treeRequest() {
        DbQueryTreeRequest request = new DbQueryTreeRequest();
        request.setModel("orders");
        return request;
    }

    private DbQueryPivotRequest pivotRequest() {
        DbQueryPivotRequest request = new DbQueryPivotRequest();
        request.setModel("orders");
        request.setRows(List.of(dimension("region", "row_region")));
        request.setColumns(List.of(dimension("month", "column_month")));
        request.setMetrics(List.of(metric("amount", "sum", "sales")));
        return request;
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

    private VirtualCatalogDescriptor catalog() {
        return new VirtualCatalogDescriptor(
                "orders",
                17L,
                List.of(
                        field("id", true),
                        field("name", false),
                        field("parent_id", false),
                        field("region", false),
                        field("month", false),
                        field("amount", false)
                ),
                List.of()
        );
    }

    private VirtualCatalogDescriptor.Field field(String code, boolean primaryKey) {
        return new VirtualCatalogDescriptor.Field(code, primaryKey, true);
    }

    @SafeVarargs
    private final VirtualQueryResponse response(long total, Map<String, Object>... rows) {
        VirtualQueryResponse response = new VirtualQueryResponse();
        response.setTotal(total);
        response.setRecords(List.of(rows));
        return response;
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }
}
