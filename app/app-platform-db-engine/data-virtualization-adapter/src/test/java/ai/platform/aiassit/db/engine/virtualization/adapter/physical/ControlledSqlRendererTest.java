package ai.platform.aiassit.db.engine.virtualization.adapter.physical;

import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommandSpec;
import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommandType;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilter;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilterOperator;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilterType;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalProjection;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalQuerySpec;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlledSqlRendererTest {

    @Test
    void rendersQueryValuesAsParametersInsteadOfSqlText() {
        String hostileValue = "paid' OR 1=1 --";
        PhysicalFilter filter = and(
                predicate("amount", PhysicalFilterOperator.GT, 100),
                predicate("status", PhysicalFilterOperator.EQ, hostileValue),
                predicateWithValues("id", PhysicalFilterOperator.IN, List.of(7L, 9L))
        );
        PhysicalQuerySpec spec = new PhysicalQuerySpec(
                "sales.orders",
                List.of(
                        new PhysicalProjection("order_id", "id"),
                        new PhysicalProjection("customer_name", "customer")
                ),
                filter,
                false,
                50
        );

        ControlledSqlRenderer.RenderedSql rendered =
                ControlledSqlRenderer.query(spec, 50, DbAccessDbType.MYSQL);

        assertEquals(
                "SELECT `order_id` AS `id`, `customer_name` AS `customer` "
                        + "FROM `sales`.`orders` WHERE (`amount` > ?) AND (`status` = ?) "
                        + "AND (`id` IN (?, ?)) LIMIT 50",
                rendered.sql()
        );
        assertEquals(List.of(100, hostileValue, 7L, 9L), rendered.parameters());
        assertFalse(rendered.sql().contains(hostileValue));
    }

    @ParameterizedTest
    @MethodSource("likeOperators")
    void rendersLikeFamilyWithControlledWildcardParameters(
            PhysicalFilterOperator operator,
            String expectedParameter
    ) {
        PhysicalQuerySpec spec = querySpec(predicate("name", operator, "alice"));

        ControlledSqlRenderer.RenderedSql rendered =
                ControlledSqlRenderer.query(spec, 10, DbAccessDbType.POSTGRESQL);

        assertTrue(rendered.sql().contains("\"name\" LIKE ?"));
        assertEquals(List.of(expectedParameter), rendered.parameters());
    }

    @Test
    void rendersInAndNotInWithOnePlaceholderPerValue() {
        PhysicalFilter filter = and(
                predicateWithValues("status", PhysicalFilterOperator.IN, List.of("PAID", "DONE")),
                predicateWithValues("region", PhysicalFilterOperator.NOT_IN, List.of("EU", "US"))
        );

        ControlledSqlRenderer.RenderedSql rendered = ControlledSqlRenderer.query(
                querySpec(filter),
                10,
                DbAccessDbType.POSTGRESQL
        );

        assertTrue(rendered.sql().contains("\"status\" IN (?, ?)"));
        assertTrue(rendered.sql().contains("\"region\" NOT IN (?, ?)"));
        assertEquals(List.of("PAID", "DONE", "EU", "US"), rendered.parameters());
    }

    @ParameterizedTest
    @MethodSource("supportedDialectQueries")
    void appliesDialectSpecificIdentifierQuotesAndRowLimit(
            DbAccessDbType dbType,
            String expectedSql
    ) {
        PhysicalQuerySpec spec = new PhysicalQuerySpec(
                "sales.orders",
                List.of(new PhysicalProjection("order_id", "id")),
                null,
                false,
                7
        );

        ControlledSqlRenderer.RenderedSql rendered = ControlledSqlRenderer.query(spec, 7, dbType);

        assertEquals(expectedSql, rendered.sql());
        assertEquals(List.of(), rendered.parameters());
    }

    @ParameterizedTest
    @EnumSource(value = DbAccessDbType.class, names = {"MYSQL", "POSTGRESQL", "ORACLE", "SQL_SERVER"})
    void rendersCountWithoutApplyingPageLimit(DbAccessDbType dbType) {
        PhysicalQuerySpec spec = new PhysicalQuerySpec(
                "sales.orders",
                List.of(),
                predicate("status", PhysicalFilterOperator.EQ, "PAID"),
                true,
                1
        );

        ControlledSqlRenderer.RenderedSql rendered = ControlledSqlRenderer.query(spec, 1, dbType);

        assertTrue(rendered.sql().startsWith("SELECT COUNT(1) AS "));
        assertFalse(rendered.sql().contains(" LIMIT "));
        assertFalse(rendered.sql().contains(" FETCH FIRST "));
        assertFalse(rendered.sql().contains(" TOP ("));
        assertEquals(List.of("PAID"), rendered.parameters());
    }

    @Test
    void rejectsUntrustedPhysicalIdentifiers() {
        PhysicalQuerySpec unsafeTable = new PhysicalQuerySpec(
                "orders; DROP TABLE users",
                List.of(new PhysicalProjection("id", "id")),
                null,
                false,
                10
        );
        PhysicalQuerySpec unsafeProjection = new PhysicalQuerySpec(
                "orders",
                List.of(new PhysicalProjection("amount + 1", "amount")),
                null,
                false,
                10
        );
        PhysicalQuerySpec unsafeAlias = new PhysicalQuerySpec(
                "orders",
                List.of(new PhysicalProjection("amount", "total amount")),
                null,
                false,
                10
        );
        PhysicalQuerySpec unsafeFilter = querySpec(
                predicate("id OR 1=1", PhysicalFilterOperator.EQ, 1L)
        );

        assertThrows(IllegalArgumentException.class,
                () -> ControlledSqlRenderer.query(unsafeTable, 10, DbAccessDbType.MYSQL));
        assertThrows(IllegalArgumentException.class,
                () -> ControlledSqlRenderer.query(unsafeProjection, 10, DbAccessDbType.MYSQL));
        assertThrows(IllegalArgumentException.class,
                () -> ControlledSqlRenderer.query(unsafeAlias, 10, DbAccessDbType.MYSQL));
        assertThrows(IllegalArgumentException.class,
                () -> ControlledSqlRenderer.query(unsafeFilter, 10, DbAccessDbType.MYSQL));
    }

    @Test
    void rejectsMongoDbForRelationalPlans() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ControlledSqlRenderer.query(querySpec(null), 10, DbAccessDbType.MONGODB)
        );
    }

    @Test
    void rendersBatchInsertAndPreservesParameterOrder() {
        Map<String, Object> first = row("id", 1L, "name", "alpha");
        Map<String, Object> second = row("id", 2L, "name", "beta");
        PhysicalCommandSpec spec = new PhysicalCommandSpec(
                PhysicalCommandType.INSERT,
                "sales.orders",
                List.of(first, second),
                Map.of(),
                null
        );

        ControlledSqlRenderer.RenderedSql rendered =
                ControlledSqlRenderer.command(spec, DbAccessDbType.MYSQL);

        assertEquals(
                "INSERT INTO `sales`.`orders` (`id`, `name`) VALUES (?, ?), (?, ?)",
                rendered.sql()
        );
        assertEquals(List.of(1L, "alpha", 2L, "beta"), rendered.parameters());
    }

    @Test
    void rendersParameterizedUpdateAndDelete() {
        Map<String, Object> assignments = row("status", "DONE", "updated_by", 7L);
        PhysicalFilter filter = predicate("id", PhysicalFilterOperator.EQ, 99L);

        ControlledSqlRenderer.RenderedSql update = ControlledSqlRenderer.command(
                new PhysicalCommandSpec(
                        PhysicalCommandType.UPDATE,
                        "orders",
                        List.of(),
                        assignments,
                        filter
                ),
                DbAccessDbType.POSTGRESQL
        );
        ControlledSqlRenderer.RenderedSql delete = ControlledSqlRenderer.command(
                new PhysicalCommandSpec(
                        PhysicalCommandType.DELETE,
                        "orders",
                        List.of(),
                        Map.of(),
                        filter
                ),
                DbAccessDbType.POSTGRESQL
        );

        assertEquals(
                "UPDATE \"orders\" SET \"status\" = ?, \"updated_by\" = ? WHERE \"id\" = ?",
                update.sql()
        );
        assertEquals(List.of("DONE", 7L, 99L), update.parameters());
        assertEquals("DELETE FROM \"orders\" WHERE \"id\" = ?", delete.sql());
        assertEquals(List.of(99L), delete.parameters());
    }

    @Test
    void refusesUnconditionalUpdateAndDelete() {
        PhysicalCommandSpec update = new PhysicalCommandSpec(
                PhysicalCommandType.UPDATE,
                "orders",
                List.of(),
                Map.of("status", "DONE"),
                null
        );
        PhysicalCommandSpec delete = new PhysicalCommandSpec(
                PhysicalCommandType.DELETE,
                "orders",
                List.of(),
                Map.of(),
                null
        );

        assertThrows(IllegalArgumentException.class,
                () -> ControlledSqlRenderer.command(update, DbAccessDbType.MYSQL));
        assertThrows(IllegalArgumentException.class,
                () -> ControlledSqlRenderer.command(delete, DbAccessDbType.MYSQL));
    }

    private static Stream<Arguments> likeOperators() {
        return Stream.of(
                Arguments.of(PhysicalFilterOperator.LIKE, "%alice%"),
                Arguments.of(PhysicalFilterOperator.STARTS_WITH, "alice%"),
                Arguments.of(PhysicalFilterOperator.ENDS_WITH, "%alice")
        );
    }

    private static Stream<Arguments> supportedDialectQueries() {
        return Stream.of(DbAccessDbType.values())
                .filter(dbType -> dbType != DbAccessDbType.MONGODB)
                .map(dbType -> Arguments.of(dbType, expectedDialectQuery(dbType)));
    }

    private static String expectedDialectQuery(DbAccessDbType dbType) {
        String open = switch (dbType) {
            case MYSQL, OCEANBASE, TDSQL, GOLDENDB -> "`";
            case SQL_SERVER -> "[";
            default -> "\"";
        };
        String close = dbType == DbAccessDbType.SQL_SERVER ? "]" : open;
        String projection = open + "order_id" + close + " AS " + open + "id" + close;
        String table = open + "sales" + close + "." + open + "orders" + close;
        if (dbType == DbAccessDbType.SQL_SERVER) {
            return "SELECT TOP (7) " + projection + " FROM " + table;
        }
        if (dbType == DbAccessDbType.ORACLE || dbType == DbAccessDbType.DM8) {
            return "SELECT " + projection + " FROM " + table + " FETCH FIRST 7 ROWS ONLY";
        }
        return "SELECT " + projection + " FROM " + table + " LIMIT 7";
    }

    private PhysicalQuerySpec querySpec(PhysicalFilter filter) {
        return new PhysicalQuerySpec(
                "orders",
                List.of(new PhysicalProjection("id", "id"), new PhysicalProjection("name", "name")),
                filter,
                false,
                10
        );
    }

    private PhysicalFilter and(PhysicalFilter... children) {
        return new PhysicalFilter(
                PhysicalFilterType.AND,
                null,
                null,
                null,
                List.of(),
                List.of(children)
        );
    }

    private PhysicalFilter predicate(String field, PhysicalFilterOperator operator, Object value) {
        return new PhysicalFilter(
                PhysicalFilterType.PREDICATE,
                field,
                operator,
                value,
                List.of(),
                List.of()
        );
    }

    private PhysicalFilter predicateWithValues(
            String field,
            PhysicalFilterOperator operator,
            List<Object> values
    ) {
        return new PhysicalFilter(
                PhysicalFilterType.PREDICATE,
                field,
                operator,
                null,
                values,
                List.of()
        );
    }

    private Map<String, Object> row(Object... pairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            row.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return row;
    }
}
