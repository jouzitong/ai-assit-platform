package ai.platform.aiassit.db.engine.meta.controller;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableFieldMetaService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta/field")
public class DbTableFieldMetaController
        extends BaseController<DbTableFieldMetaDTO, DbTableFieldMetaQueryRequest, DbTableFieldMetaService> {

    private final DbTableFieldMetaService service;

    public DbTableFieldMetaController(DbTableFieldMetaService service) {
        this.service = service;
    }

    @Override
    protected DbTableFieldMetaService service() {
        return service;
    }
}
