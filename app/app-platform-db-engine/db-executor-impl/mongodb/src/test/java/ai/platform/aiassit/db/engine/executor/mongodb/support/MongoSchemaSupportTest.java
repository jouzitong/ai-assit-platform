package ai.platform.aiassit.db.engine.executor.mongodb.support;

import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbColumnMeta;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableColumnDefinition;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoSchemaSupportTest {

    @Test
    void mergesColumnsWithoutDroppingExistingValidatorExpressions() throws Exception {
        Document existing = new Document("$expr", new Document("$gt", List.of("$amount", 0)));
        DbTableColumnDefinition name = DbTableColumnDefinition.builder()
                .columnName("name")
                .dataType("VARCHAR")
                .columnLength(64)
                .nullable(false)
                .columnComment("display name")
                .build();

        Document merged = MongoSchemaSupport.mergeColumns(existing, List.of(name), "users");
        assertTrue(merged.containsKey("$expr"));
        Document schema = merged.get("$jsonSchema", Document.class);
        assertEquals("users", schema.getString("description"));
        assertEquals(List.of("name"), schema.getList("required", String.class));
        Document property = schema.get("properties", Document.class).get("name", Document.class);
        assertEquals("string", property.getString("bsonType"));
        assertEquals(64, property.getInteger("maxLength"));
    }

    @Test
    void nullableColumnAllowsNullAndIsNotRequired() throws Exception {
        DbTableColumnDefinition column = DbTableColumnDefinition.builder()
                .columnName("score")
                .dataType("DECIMAL")
                .nullable(true)
                .build();
        Document validator = MongoSchemaSupport.mergeColumns(new Document(), List.of(column), null);
        Document schema = validator.get("$jsonSchema", Document.class);
        Document property = schema.get("properties", Document.class).get("score", Document.class);
        assertEquals(List.of("decimal", "null"), property.getList("bsonType", String.class));
        assertFalse(schema.containsKey("required"));
    }

    @Test
    void mapsValidatorBackToColumnMetadata() throws Exception {
        Document validator = new Document("$jsonSchema", new Document("bsonType", "object")
                .append("required", List.of("_id"))
                .append("properties", new Document("_id", new Document("bsonType", "objectId"))
                        .append("createdAt", new Document("bsonType", List.of("date", "null"))
                                .append("description", "created"))));
        List<DbColumnMeta> columns = MongoSchemaSupport.columnsFromValidator("users", validator);
        assertEquals(2, columns.size());
        assertEquals("OBJECT_ID", columns.get(0).getDataType());
        assertTrue(columns.get(0).getPrimaryKey());
        assertEquals("DATE_TIME", columns.get(1).getDataType());
        assertTrue(columns.get(1).getNullable());
    }

    @Test
    void removesPropertiesAndRequiredEntry() throws Exception {
        Document validator = new Document("$jsonSchema", new Document("properties",
                new Document("legacy", new Document("bsonType", "string"))
                        .append("keep", new Document("bsonType", "int")))
                .append("required", List.of("legacy", "keep")));
        MongoSchemaSupport.RemoveResult result = MongoSchemaSupport.removeColumns(validator, List.of("legacy"));
        Document schema = result.validator().get("$jsonSchema", Document.class);
        assertTrue(result.changed());
        assertFalse(schema.get("properties", Document.class).containsKey("legacy"));
        assertEquals(List.of("keep"), schema.getList("required", String.class));
    }

    @Test
    void rejectsUnsupportedRelationalColumnFeatures() {
        assertThrows(DbAccessException.class, () -> MongoSchemaSupport.buildProperty(
                DbTableColumnDefinition.builder().columnName("seq").dataType("BIGINT").autoIncrement(true).build()));
        assertThrows(DbAccessException.class, () -> MongoSchemaSupport.buildProperty(
                DbTableColumnDefinition.builder().columnName("state").dataType("STRING").defaultValue("NEW").build()));
        assertThrows(DbAccessException.class, () -> MongoSchemaSupport.buildProperty(
                DbTableColumnDefinition.builder().columnName("otherId").dataType("STRING").primaryKey(true).build()));
    }

    @Test
    void rejectsNestedJsonSchemaThatCannotBeSafelyMerged() {
        Document complex = new Document("$and", List.of(new Document("$jsonSchema",
                new Document("properties", new Document("name", new Document("bsonType", "string"))))));
        assertThrows(DbAccessException.class, () -> MongoSchemaSupport.mergeColumns(complex, List.of(
                DbTableColumnDefinition.builder().columnName("age").dataType("INT").build()), null));
    }

    @Test
    void partialColumnUpdatePreservesCustomConstraintsAndRequiredState() throws Exception {
        Document existing = new Document("$jsonSchema", new Document("bsonType", "object")
                .append("required", List.of("name"))
                .append("properties", new Document("name", new Document("bsonType", "string")
                        .append("pattern", "^[A-Z]")
                        .append("enum", List.of("Alice", "Bob")))));
        DbTableColumnDefinition patch = DbTableColumnDefinition.builder()
                .columnName("name")
                .columnComment("display name")
                .build();

        Document merged = MongoSchemaSupport.mergeColumns(existing, List.of(patch), null);
        Document schema = merged.get("$jsonSchema", Document.class);
        Document property = schema.get("properties", Document.class).get("name", Document.class);
        assertEquals("^[A-Z]", property.getString("pattern"));
        assertEquals(List.of("Alice", "Bob"), property.getList("enum", String.class));
        assertEquals("display name", property.getString("description"));
        assertEquals(List.of("name"), schema.getList("required", String.class));
    }
}
