package ai.platform.aiassit.db.engine.virtualization.adapter.compat;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyFilterAstParserTest {

    private final LegacyFilterAstParser parser = new LegacyFilterAstParser();

    @Test
    void returnsNullForEmptyFilter() {
        assertNull(parser.parse(null, null));
    }

    @Test
    void parsesScalarConditionObjectAliasesAndExpressionPrecedence() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", "ACTIVE");
        filters.put("score", Map.of("op", "ge", "value", 80));
        filters.put("tenant.id", Map.of("op", "neq", "value", 9));

        FilterNode root = parser.parse(filters, "status or score and tenant.id");

        assertEquals(FilterType.OR, root.getType());
        assertEquals(FilterOperator.EQ, root.getChildren().get(0).getOperator());
        FilterNode and = root.getChildren().get(1);
        assertEquals(FilterType.AND, and.getType());
        assertEquals(FilterOperator.GTE, and.getChildren().get(0).getOperator());
        assertEquals(FilterOperator.NE, and.getChildren().get(1).getOperator());
    }

    @Test
    void parsesInValuesAndDefaultsToAnd() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", Map.of("op", "in", "value", List.of("A", "B")));
        filters.put("deleted", Map.of("value", false));

        FilterNode root = parser.parse(filters, null);

        assertEquals(FilterType.AND, root.getType());
        assertEquals(List.of("A", "B"), root.getChildren().get(0).getValues());
        assertEquals(FilterOperator.EQ, root.getChildren().get(1).getOperator());
    }

    @Test
    void requiresExpressionToReferenceExactlyAllFilterKeys() {
        LegacyQueryCompatibilityException exception = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> parser.parse(Map.of("a", 1, "b", 2), "a")
        );

        assertEquals(LegacyFilterAstParser.INVALID_FILTER, exception.getCode());
    }

    @Test
    void rejectsEmptyInAndIllegalExpressionCharacters() {
        assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> parser.parse(Map.of("id", Map.of("op", "in", "value", List.of())), null)
        );
        assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> parser.parse(Map.of("id", 1), "id && id")
        );
    }

    @Test
    void mapsPrefixAndSuffixWithoutDowngradingThemToContains() {
        FilterNode prefix = parser.parse(
                Map.of("name", Map.of("op", "prefix_like", "value", "A")),
                null
        );
        FilterNode suffix = parser.parse(
                Map.of("name", Map.of("op", "suffix_like", "value", "Z")),
                null
        );

        assertEquals("STARTS_WITH", prefix.getOperator().name());
        assertEquals("ENDS_WITH", suffix.getOperator().name());
    }

    @Test
    void mapsEveryPublishedLegacyOperatorAndAlias() {
        OperatorCase[] cases = {
                new OperatorCase("eq", FilterOperator.EQ, 1),
                new OperatorCase("ne", FilterOperator.NE, 1),
                new OperatorCase("neq", FilterOperator.NE, 1),
                new OperatorCase("gt", FilterOperator.GT, 1),
                new OperatorCase("gte", FilterOperator.GTE, 1),
                new OperatorCase("ge", FilterOperator.GTE, 1),
                new OperatorCase("lt", FilterOperator.LT, 1),
                new OperatorCase("lte", FilterOperator.LTE, 1),
                new OperatorCase("le", FilterOperator.LTE, 1),
                new OperatorCase("like", FilterOperator.LIKE, "x"),
                new OperatorCase("prefix_like", FilterOperator.STARTS_WITH, "x"),
                new OperatorCase("suffix_like", FilterOperator.ENDS_WITH, "x"),
                new OperatorCase("in", FilterOperator.IN, List.of(1)),
                new OperatorCase("not_in", FilterOperator.NOT_IN, List.of(1)),
                new OperatorCase("is_null", FilterOperator.IS_NULL, null),
                new OperatorCase("is_not_null", FilterOperator.IS_NOT_NULL, null)
        };

        for (OperatorCase operatorCase : cases) {
            Map<String, Object> condition = new LinkedHashMap<>();
            condition.put("op", operatorCase.legacy());
            condition.put("value", operatorCase.value());
            FilterNode result = parser.parse(Map.of("field", condition), null);
            assertEquals(operatorCase.expected(), result.getOperator(), operatorCase.legacy());
        }
    }

    private record OperatorCase(String legacy, FilterOperator expected, Object value) {
    }
}
