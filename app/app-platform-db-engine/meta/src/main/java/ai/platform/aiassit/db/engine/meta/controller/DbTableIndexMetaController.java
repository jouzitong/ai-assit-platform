package ai.platform.aiassit.db.engine.meta.controller;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableIndexMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableIndexMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableIndexMetaService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta/index")
public class DbTableIndexMetaController
        extends BaseController<DbTableIndexMetaDTO, DbTableIndexMetaQueryRequest, DbTableIndexMetaService> {

    private final DbTableIndexMetaService service;

    public DbTableIndexMetaController(DbTableIndexMetaService service) {
        this.service = service;
    }

    @Override
    protected DbTableIndexMetaService service() {
        return service;
    }
}
