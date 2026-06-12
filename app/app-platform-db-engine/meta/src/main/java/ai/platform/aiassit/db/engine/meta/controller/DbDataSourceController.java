package ai.platform.aiassit.db.engine.meta.controller;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbDataSourceDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbDataSourceQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbDataSourceService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
