package ai.platform.aiassit.db.engine.core.controller;

import ai.platform.aiassit.db.engine.api.DbQueryApi;
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
import ai.platform.aiassit.db.engine.core.service.DbQueryService;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class DbQueryController implements DbQueryApi {

    private final DbQueryService dbQueryService;

    public DbQueryController(DbQueryService dbQueryService) {
        this.dbQueryService = dbQueryService;
    }

    @Override
    @PostMapping("/api/v1/query.get")
    public R<DbQueryGetResponse> queryGet(@RequestBody DbQueryGetRequest request) {
        return R.ok(dbQueryService.queryGet(request));
    }

    @Override
    @PostMapping("/api/v1/query.list")
    public R<DbQueryListResponse> queryList(@RequestBody DbQueryListRequest request) {
        return R.ok(dbQueryService.queryList(request));
    }

    @Override
    @PostMapping("/api/v1/query.count")
    public R<DbQueryCountResponse> queryCount(@RequestBody DbQueryCountRequest request) {
        return R.ok(dbQueryService.queryCount(request));
    }

    @Override
    @PostMapping("/api/v1/query.aggregate")
    public R<DbQueryAggregateResponse> queryAggregate(@RequestBody DbQueryAggregateRequest request) {
        return R.ok(dbQueryService.queryAggregate(request));
    }

    @Override
    @PostMapping("/api/v1/query.tree")
    public R<DbQueryTreeResponse> queryTree(@RequestBody DbQueryTreeRequest request) {
        return R.ok(dbQueryService.queryTree(request));
    }

    @Override
    @PostMapping("/api/v1/query.pivot")
    public R<DbQueryPivotResponse> queryPivot(@RequestBody DbQueryPivotRequest request) {
        return R.ok(dbQueryService.queryPivot(request));
    }
}
