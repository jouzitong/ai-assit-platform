package ai.platform.aiassit.db.engine.virtualization.adapter.compat;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryListResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyResponseAssemblerTest {

    private final LegacyResponseAssembler assembler = new LegacyResponseAssembler();

    @Test
    void assemblesGetWithProjectedNestedRelationAndDropsInternalFields() {
        VirtualQueryResponse source = response(1L, row(
                "id", 1L,
                "customer.name", "Alice",
                "customer.mobile", null,
                "__internal_join_key", 99L
        ));

        DbQueryGetResponse response = assembler.assembleGet(
                source,
                List.of("id", "customer.name", "customer.mobile")
        );

        assertEquals(1L, response.getRecord().get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> customer = (Map<String, Object>) response.getRecord().get("customer");
        assertEquals("Alice", customer.get("name"));
        assertNull(customer.get("mobile"));
        assertFalse(response.getRecord().containsKey("__internal_join_key"));
    }

    @Test
    void collapsesAllNullRelationAndKeepsListSummaryEmpty() {
        VirtualQueryResponse source = response(13L, row("id", 1L, "customer.name", null));
        source.setSummary(Map.of("unexpected", 1));

        DbQueryListResponse response = assembler.assembleList(
                source,
                2,
                5,
                List.of("id", "customer.name")
        );

        assertNull(response.getList().get(0).get("customer"));
        assertEquals(13L, response.getPageInfo().getTotal());
        assertEquals(2, response.getPageInfo().getPage());
        assertEquals(5, response.getPageInfo().getSize());
        assertEquals(Map.of(), response.getSummary());
    }

    @Test
    void assemblesCollectionRelationAsNestedArray() {
        VirtualQueryResponse source = response(1L, row(
                "id", 1L,
                "items", List.of(
                        row("sku", "A", "quantity", 2),
                        row("sku", "B", "quantity", 3)
                )
        ));

        DbQueryListResponse response = assembler.assembleList(
                source,
                1,
                20,
                List.of("id", "items.sku", "items.quantity")
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getList().get(0).get("items");
        assertEquals(List.of("A", "B"), items.stream().map(item -> item.get("sku")).toList());
        assertEquals(List.of(2, 3), items.stream().map(item -> item.get("quantity")).toList());
    }

    @Test
    void keepsUnmatchedCollectionRelationAsEmptyArray() {
        VirtualQueryResponse source = response(1L, row("id", 1L, "items", List.of()));

        DbQueryListResponse response = assembler.assembleList(
                source,
                1,
                20,
                List.of("id", "items.sku")
        );

        assertEquals(List.of(), response.getList().get(0).get("items"));
    }

    @Test
    void synthesizesPlainCountRecordsAndSummary() {
        VirtualQueryResponse source = response(42L);

        DbQueryCountResponse response = assembler.assembleCount(source, 1, 10, true);

        assertEquals(42L, response.getTotal());
        assertEquals(List.of(Map.of("count", 42L)), response.getRecords());
        assertEquals(Map.of("count", 42L), response.getSummary());
    }

    @Test
    void keepsGroupedCountRecordsAndSummaryFlat() {
        VirtualQueryResponse source = response(7L, row("customer.name", "Alice", "orders", 3L));
        source.setSummary(Map.of("orders", 10L));

        DbQueryCountResponse response = assembler.assembleCount(source, 2, 20, false);

        assertEquals("Alice", response.getRecords().get(0).get("customer.name"));
        assertEquals(Map.of("orders", 10L), response.getSummary());
        assertEquals(7L, response.getTotal());
    }

    @Test
    void keepsAggregateResultsFlat() {
        VirtualQueryResponse source = response(3L, row("customer.name", "Alice", "amount", 10));
        source.setSummary(Map.of("amount", 10));

        DbQueryAggregateResponse response = assembler.assembleAggregate(source, 1, 10);

        assertEquals("Alice", response.getRecords().get(0).get("customer.name"));
        assertEquals(Map.of("amount", 10), response.getSummary());
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
