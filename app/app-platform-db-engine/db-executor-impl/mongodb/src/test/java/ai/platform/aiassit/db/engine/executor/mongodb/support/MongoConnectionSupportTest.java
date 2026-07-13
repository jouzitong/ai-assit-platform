package ai.platform.aiassit.db.engine.executor.mongodb.support;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessDatabase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MongoConnectionSupportTest {

    @Test
    void rejectsUnacknowledgedWriteConcernBeforeOpeningClient() {
        MongoConnectionSupport support = new MongoConnectionSupport();
        DbAccessContext context = DbAccessContext.builder()
                .dbType(DbAccessDbType.MONGODB)
                .database(DbAccessDatabase.builder()
                        .jdbcUrl("mongodb://localhost:27017/app?w=0")
                        .build())
                .build();

        assertThrows(DbAccessException.class, () -> support.databaseName(context, null));
        support.destroy();
    }
}
