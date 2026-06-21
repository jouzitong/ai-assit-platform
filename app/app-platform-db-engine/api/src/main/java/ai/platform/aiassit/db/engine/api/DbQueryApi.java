package ai.platform.aiassit.db.engine.api;

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
import org.athena.framework.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 数据查询接口客户端。
 *
 * <p>该接口用于通过 Feign 调用数据库引擎服务，统一提供详情查询、列表查询、数量统计、聚合统计、树形查询、透视查询等能力。</p>
 * <p>所有接口返回值统一使用 {@link R} 包装，便于前端和调用方按照统一响应结构处理结果、错误码和提示信息。</p>
 */
@FeignClient(
        name = "dbEngine",
        contextId = "platformDbEngineQueryClient",
        path = "/dbEngine"
)
public interface DbQueryApi {

    /**
     * 查询单条数据详情。
     *
     * <p>通常用于根据主键、唯一条件或业务条件查询某个模型的一条记录，并返回指定字段信息。</p>
     *
     * @param request 详情查询请求参数，包含模型、过滤条件、返回字段等信息
     * @return 统一响应结果，data 为单条数据详情查询结果
     */
    @PostMapping("/api/v1/query.get")
    R<DbQueryGetResponse> queryGet(@RequestBody DbQueryGetRequest request);

    /**
     * 查询数据列表。
     *
     * <p>通常用于分页查询某个模型的数据集合，支持过滤条件、排序字段、返回字段、关联关系等扩展参数。</p>
     *
     * @param request 列表查询请求参数，包含模型、过滤条件、分页信息、排序信息等
     * @return 统一响应结果，data 为列表查询结果
     */
    @PostMapping("/api/v1/query.list")
    R<DbQueryListResponse> queryList(@RequestBody DbQueryListRequest request);

    /**
     * 查询数据数量。
     *
     * <p>通常用于根据模型和过滤条件统计符合条件的数据条数，可用于分页总数、统计卡片等场景。</p>
     *
     * @param request 数量统计请求参数，包含模型、过滤条件等信息
     * @return 统一响应结果，data 为数量统计结果
     */
    @PostMapping("/api/v1/query.count")
    R<DbQueryCountResponse> queryCount(@RequestBody DbQueryCountRequest request);

    /**
     * 执行聚合统计查询。
     *
     * <p>通常用于根据指定字段进行求和、平均值、最大值、最小值、分组统计等聚合分析。</p>
     *
     * @param request 聚合查询请求参数，包含模型、过滤条件、聚合字段、分组字段等信息
     * @return 统一响应结果，data 为聚合统计查询结果
     */
    @PostMapping("/api/v1/query.aggregate")
    R<DbQueryAggregateResponse> queryAggregate(@RequestBody DbQueryAggregateRequest request);

    /**
     * 查询树形结构数据。
     *
     * <p>通常用于查询组织架构、部门层级、分类目录等具有父子关系的数据，并返回树形结构结果。</p>
     *
     * @param request 树形查询请求参数，包含模型、过滤条件、节点标识、父节点标识等信息
     * @return 统一响应结果，data 为树形结构查询结果
     */
    @PostMapping("/api/v1/query.tree")
    R<DbQueryTreeResponse> queryTree(@RequestBody DbQueryTreeRequest request);

    /**
     * 执行透视查询。
     *
     * <p>通常用于交叉表、行列维度分析、指标透视等场景，将明细数据转换为便于展示和分析的透视结果。</p>
     *
     * @param request 透视查询请求参数，包含模型、过滤条件、行维度、列维度、指标字段等信息
     * @return 统一响应结果，data 为透视查询结果
     */
    @PostMapping("/api/v1/query.pivot")
    R<DbQueryPivotResponse> queryPivot(@RequestBody DbQueryPivotRequest request);
}
