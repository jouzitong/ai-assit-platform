package ai.platform.aiassit.db.engine.executor.mongodb.support;

import ai.platform.aiassit.db.engine.executor.spi.model.DbQueryColumn;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MongoBsonSupportTest {

    @Test
    void convertsBsonSpecificValuesToJsonSafeValues() {
        ObjectId id = new ObjectId("64b000000000000000000001");
        Document source = new Document("_id", id)
                .append("amount", new Decimal128(new BigDecimal("12.34")))
                .append("createdAt", Date.from(Instant.parse("2024-01-02T03:04:05Z")))
                .append("payload", new Binary(new byte[]{1, 2, 3}))
                .append("nested", new Document("enabled", true));

        Map<String, Object> safe = MongoBsonSupport.toSafeDocument(source);
        assertEquals(id.toHexString(), safe.get("_id"));
        assertEquals(new BigDecimal("12.34"), safe.get("amount"));
        assertEquals("2024-01-02T03:04:05Z", safe.get("createdAt"));
        assertEquals("AQID", safe.get("payload"));
        assertInstanceOf(Map.class, safe.get("nested"));
    }

    @Test
    void marksMixedQueryColumnTypesAsOther() {
        List<DbQueryColumn> columns = MongoBsonSupport.queryColumns(List.of(
                new Document("value", 1),
                new Document("value", "one")
        ));
        assertEquals(1, columns.size());
        assertEquals("MIXED", columns.get(0).getTypeName());
        assertEquals(Types.OTHER, columns.get(0).getJdbcType());
    }

    @Test
    void keepsFirstSeenColumnOrderAcrossDocuments() {
        List<DbQueryColumn> columns = MongoBsonSupport.queryColumns(List.of(
                new Document("first", 1).append("second", true),
                new Document("third", 3L).append("first", 2)
        ));
        assertEquals(List.of("first", "second", "third"),
                columns.stream().map(DbQueryColumn::getName).toList());
    }
}
