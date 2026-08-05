package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

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

/**
 * 保持历史查询契约兼容的虚拟数据查询门面。
 *
 * <p>保留 {@link DbQueryApi} 的 URL 与 DTO，实际执行统一收口到虚拟目录、字段策略和权限控制之后，避免历史调用方绕过虚拟层访问物理数据。</p>
 */
@RestController
@RequestMapping
public class DbQueryController implements DbQueryApi {

    private final DbQueryService dbQueryService;

    public DbQueryController(DbQueryService dbQueryService) {
        this.dbQueryService = dbQueryService;
    }

    /**
     * 查询符合条件的一条虚拟数据记录。
     *
     * @param request 详情查询请求体，包含虚拟模型、条件与需要返回的字段
     * @return 包装后的单条查询结果
     */
    @Override
    @PostMapping("/api/v1/query.get")
    public R<DbQueryGetResponse> queryGet(@RequestBody DbQueryGetRequest request) {
        return R.ok(dbQueryService.queryGet(request));
    }

    /**
     * 分页查询虚拟数据记录列表。
     *
     * @param request 列表查询请求体，包含虚拟模型、筛选、排序、分页和字段选择
     * @return 包装后的列表查询结果及分页信息
     */
    @Override
    @PostMapping("/api/v1/query.list")
    public R<DbQueryListResponse> queryList(@RequestBody DbQueryListRequest request) {
        return R.ok(dbQueryService.queryList(request));
    }

    /**
     * 统计符合虚拟查询条件的数据数量。
     *
     * @param request 计数请求体，包含虚拟模型和筛选条件
     * @return 包装后的计数结果
     */
    @Override
    @PostMapping("/api/v1/query.count")
    public R<DbQueryCountResponse> queryCount(@RequestBody DbQueryCountRequest request) {
        return R.ok(dbQueryService.queryCount(request));
    }

    /**
     * 在虚拟数据范围内执行分组或指标聚合。
     *
     * @param request 聚合请求体，包含筛选、分组维度和聚合指标
     * @return 包装后的聚合结果
     */
    @Override
    @PostMapping("/api/v1/query.aggregate")
    public R<DbQueryAggregateResponse> queryAggregate(@RequestBody DbQueryAggregateRequest request) {
        return R.ok(dbQueryService.queryAggregate(request));
    }

    /**
     * 查询具有父子关系的虚拟数据并组织为树。
     *
     * @param request 树形查询请求体，包含节点、父节点和筛选条件
     * @return 包装后的树形查询结果
     */
    @Override
    @PostMapping("/api/v1/query.tree")
    public R<DbQueryTreeResponse> queryTree(@RequestBody DbQueryTreeRequest request) {
        return R.ok(dbQueryService.queryTree(request));
    }

    /**
     * 按行列维度和指标执行虚拟数据透视查询。
     *
     * @param request 透视请求体，包含筛选、行列维度和聚合指标
     * @return 包装后的透视结果，适用于交叉分析展示
     */
    @Override
    @PostMapping("/api/v1/query.pivot")
    public R<DbQueryPivotResponse> queryPivot(@RequestBody DbQueryPivotRequest request) {
        return R.ok(dbQueryService.queryPivot(request));
    }
}
