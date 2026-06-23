package ai.platform.aiassit.db.engine.executor.spi.model;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbAccessContext {

    private String sourceKey;

    private String sourceName;

    private DbAccessSourceType sourceType;

    private DbAccessDbType dbType;

    private String endpoint;

    private DbAccessNetwork network;

    private DbAccessAuth auth;

    private DbAccessDatabase database;

    private Map<String, Object> attributes;
}
