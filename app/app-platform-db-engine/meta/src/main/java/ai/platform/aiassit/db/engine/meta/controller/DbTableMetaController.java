package ai.platform.aiassit.db.engine.meta.controller;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableMetaService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta/table")
public class DbTableMetaController
        extends BaseController<DbTableMetaDTO, DbTableMetaQueryRequest, DbTableMetaService> {

    private final DbTableMetaService service;

    public DbTableMetaController(DbTableMetaService service) {
        this.service = service;
    }

    @Override
    protected DbTableMetaService service() {
        return service;
    }
}
