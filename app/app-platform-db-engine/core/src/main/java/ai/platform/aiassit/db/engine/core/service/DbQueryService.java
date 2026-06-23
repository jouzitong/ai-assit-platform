package ai.platform.aiassit.db.engine.core.service;

import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryListRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryListResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeResponse;

public interface DbQueryService {

    DbQueryGetResponse queryGet(DbQueryGetRequest request);

    DbQueryListResponse queryList(DbQueryListRequest request);

    DbQueryCountResponse queryCount(DbQueryCountRequest request);

    DbQueryAggregateResponse queryAggregate(DbQueryAggregateRequest request);

    DbQueryTreeResponse queryTree(DbQueryTreeRequest request);

    DbQueryPivotResponse queryPivot(DbQueryPivotRequest request);
}
