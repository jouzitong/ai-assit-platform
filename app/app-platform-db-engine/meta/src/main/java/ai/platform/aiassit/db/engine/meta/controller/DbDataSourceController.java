package ai.platform.aiassit.db.engine.meta.controller;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbDataSourceDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbDataSourceQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbDataSourceService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据源连接配置的通用 CRUD 接口。
 *
 * <p>复用 {@link BaseController} 提供的新增、详情、分页、更新和删除能力；请求体使用 {@link DbDataSourceDTO}，查询条件使用
 * {@link DbDataSourceQueryRequest}，响应返回连接配置及其管理状态。</p>
 */
@RestController
@RequestMapping("/api/v1/meta/data-source")
public class DbDataSourceController
        extends BaseController<DbDataSourceDTO, DbDataSourceQueryRequest, DbDataSourceService> {

    private final DbDataSourceService service;

    public DbDataSourceController(DbDataSourceService service) {
        this.service = service;
    }

    @Override
    protected DbDataSourceService service() {
        return service;
    }
}
