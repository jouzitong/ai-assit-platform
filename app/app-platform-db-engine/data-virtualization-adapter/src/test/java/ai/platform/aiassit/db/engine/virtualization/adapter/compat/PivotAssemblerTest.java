package ai.platform.aiassit.db.engine.virtualization.adapter.compat;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountDimension;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountMetric;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotExt;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PivotAssemblerTest {

    private final PivotAssembler assembler = new PivotAssembler();

    @Test
    void pivotsMultipleMetricsAndAppliesFillValue() {
        DbQueryPivotRequest request = request(
                List.of(dimension("region", "region")),
                List.of(dimension("month", "month")),
                List.of(metric("amount", "sum", "sales"), metric(null, "count", "orders"))
        );
        request.getExt().setFillValue(0);
        VirtualQueryResponse source = response(
                row("region", "CN", "month", "01", "sales", 100, "orders", 2),
                row("region", "CN", "month", "02", "sales", 120, "orders", 3),
                row("region", "US", "month", "01", "sales", 80, "orders", 1)
        );

        DbQueryPivotResponse response = assembler.assemble(source, request);

        assertEquals(List.of("01:sales", "01:orders", "02:sales", "02:orders"), response.getColumnKeys());
        assertEquals(0, response.getRecords().get(1).get("02:sales"));
        assertEquals(1, response.getRecords().get(1).get("01:orders"));
    }

    @Test
    void usesStructuralRowKeysInsteadOfJoinedStrings() {
        DbQueryPivotRequest request = request(
                List.of(dimension("r1", null), dimension("r2", null)),
                List.of(dimension("month", null)),
                List.of(metric("amount", "sum", "sales"))
        );
        VirtualQueryResponse source = response(
                row("r1", "a|b", "r2", "c", "month", "01", "sales", 1),
                row("r1", "a", "r2", "b|c", "month", "01", "sales", 2)
        );

        DbQueryPivotResponse response = assembler.assemble(source, request);

        assertEquals(2, response.getRecords().size());
        assertEquals(List.of(1, 2), response.getRecords().stream().map(row -> row.get("01")).toList());
    }

    @Test
    void rejectsTimeGrainAndTopNUntilTheirSemanticsAreVersioned() {
        DbQueryPivotRequest timeGrain = request(
                List.of(dimension("region", null)),
                List.of(dimension("month", null)),
                List.of(metric("amount", "sum", "sales"))
        );
        timeGrain.getExt().setTimeGrain("month");
        DbQueryPivotRequest topN = request(
                List.of(dimension("region", null)),
                List.of(dimension("month", null)),
                List.of(metric("amount", "sum", "sales"))
        );
        topN.getExt().setTopN(10);

        LegacyQueryCompatibilityException timeException = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> assembler.assemble(response(), timeGrain)
        );
        LegacyQueryCompatibilityException topNException = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> assembler.assemble(response(), topN)
        );
        assertEquals(PivotAssembler.UNSUPPORTED_OPTION, timeException.getCode());
        assertEquals(PivotAssembler.UNSUPPORTED_OPTION, topNException.getCode());
    }

    private DbQueryPivotRequest request(
            List<DbQueryCountDimension> rows,
            List<DbQueryCountDimension> columns,
            List<DbQueryCountMetric> metrics
    ) {
        DbQueryPivotRequest request = new DbQueryPivotRequest();
        request.setRows(rows);
        request.setColumns(columns);
        request.setMetrics(metrics);
        request.setExt(new DbQueryPivotExt());
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

    @SafeVarargs
    private final VirtualQueryResponse response(Map<String, Object>... rows) {
        VirtualQueryResponse response = new VirtualQueryResponse();
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
