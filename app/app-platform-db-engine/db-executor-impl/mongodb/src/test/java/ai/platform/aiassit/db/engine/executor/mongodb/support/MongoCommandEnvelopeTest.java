package ai.platform.aiassit.db.engine.executor.mongodb.support;

import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoCommandEnvelopeTest {

    @Test
    void parsesFindAndReplacesTypedParameter() throws Exception {
        MongoCommandEnvelope.QueryCommand command = MongoCommandEnvelope.parseQuery("""
                {
                  "operation": "find",
                  "collection": "orders",
                  "filter": {"tenantId": {"$param": 0}},
                  "sort": {"createdAt": -1},
                  "limit": 20
                }
                """, List.of(42L));

        assertEquals("find", command.operation());
        assertEquals(42L, command.filter().get("tenantId"));
        assertEquals(20, command.limit());
    }

    @Test
    void rejectsUnusedAndOutOfRangeParameters() {
        assertThrows(DbAccessException.class, () -> MongoCommandEnvelope.parseQuery("""
                {"operation":"find","collection":"orders","filter":{}}
                """, List.of("unused")));
        assertThrows(DbAccessException.class, () -> MongoCommandEnvelope.parseQuery("""
                {"operation":"find","collection":"orders","filter":{"id":{"$param":1}}}
                """, List.of("only-zero")));
    }

    @Test
    void rejectsWritingAggregationAndServerSideCode() {
        DbAccessException out = assertThrows(DbAccessException.class, () -> MongoCommandEnvelope.parseQuery("""
                {"operation":"aggregate","collection":"orders","pipeline":[{"$out":"archive"}]}
                """, List.of()));
        assertTrue(out.getMessage().contains("$out"));

        DbAccessException where = assertThrows(DbAccessException.class, () -> MongoCommandEnvelope.parseQuery("""
                {"operation":"find","collection":"orders","filter":{"$where":"return true"}}
                """, List.of()));
        assertTrue(where.getMessage().contains("$where"));

        DbAccessException lookup = assertThrows(DbAccessException.class, () -> MongoCommandEnvelope.parseQuery("""
                {"operation":"aggregate","collection":"orders","pipeline":[{"$lookup":{"from":"users","as":"users"}}]}
                """, List.of()));
        assertTrue(lookup.getMessage().contains("$lookup"));
    }

    @Test
    void acceptsWhitelistedUpdateAndRejectsUnknownOperator() throws Exception {
        MongoCommandEnvelope.ExecuteCommand command = MongoCommandEnvelope.parseExecute("""
                {
                  "operation":"updateMany",
                  "collection":"orders",
                  "filter":{"tenantId":{"$param":0}},
                  "update":{"$set":{"archived":true}},
                  "upsert":false
                }
                """, List.of("tenant-a"));
        assertEquals("tenant-a", command.filter().get("tenantId"));
        assertTrue(command.update().containsKey("$set"));

        assertThrows(DbAccessException.class, () -> MongoCommandEnvelope.parseExecute("""
                {
                  "operation":"updateOne",
                  "collection":"orders",
                  "filter":{},
                  "update":{"$replaceWith":{"status":"x"}}
                }
                """, List.of()));
    }

    @Test
    void trimsBeforeRejectingSystemCollection() {
        assertThrows(DbAccessException.class, () -> MongoCommandEnvelope.requireCollection(" system.users "));
        assertThrows(DbAccessException.class, () -> MongoCommandEnvelope.parseExecute("""
                {"operation":"deleteMany","collection":" system.users ","filter":{}}
                """, List.of()));
    }

    @Test
    void requiresExplicitMutationPayloadsAndRejectsUnknownFields() {
        assertThrows(DbAccessException.class, () -> MongoCommandEnvelope.parseExecute("""
                {"operation":"insertOne","collection":"orders"}
                """, List.of()));
        assertThrows(DbAccessException.class, () -> MongoCommandEnvelope.parseQuery("""
                {"operation":"find","collection":"orders","filter":{},"rawCommand":true}
                """, List.of()));

        assertThrows(DbAccessException.class, () -> MongoCommandEnvelope.parseExecute("""
                {"operation":"deleteMany","collection":"orders","filter":{}}
                """, List.of()));
    }

    @Test
    void allDocumentMutationRequiresExplicitFlag() throws Exception {
        MongoCommandEnvelope.ExecuteCommand command = MongoCommandEnvelope.parseExecute("""
                {
                  "operation":"updateMany",
                  "collection":"orders",
                  "filter":{},
                  "update":{"$set":{"archived":true}},
                  "allowAllDocuments":true
                }
                """, List.of());
        assertTrue(command.filter().isEmpty());
    }
}
