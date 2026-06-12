package ai.platform.aiassit.db.engine.api;

import ai.platform.aiassit.db.engine.api.dto.DbQueryListRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryListResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "dbEngine",
        contextId = "platformDbEngineQueryClient",
        path = "/dbEngine"
)
public interface DbQueryApi {

    @PostMapping("/api/v1/query/get")
    DbQueryGetResponse queryGet(@RequestBody DbQueryGetRequest request);

    @PostMapping("/api/v1/query/list")
    DbQueryListResponse queryList(@RequestBody DbQueryListRequest request);

    @PostMapping("/api/v1/query/count")
    DbQueryCountResponse queryCount(@RequestBody DbQueryCountRequest request);

    @PostMapping("/api/v1/query/aggregate")
    DbQueryAggregateResponse queryAggregate(@RequestBody DbQueryAggregateRequest request);

    @PostMapping("/api/v1/query/tree")
    DbQueryTreeResponse queryTree(@RequestBody DbQueryTreeRequest request);

    @PostMapping("/api/v1/query/pivot")
    DbQueryPivotResponse queryPivot(@RequestBody DbQueryPivotRequest request);
}
