package ai.platform.aiassit.db.engine.meta.controller;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableRelationMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableRelationMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableRelationMetaService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta/relation")
public class DbTableRelationMetaController
        extends BaseController<DbTableRelationMetaDTO, DbTableRelationMetaQueryRequest, DbTableRelationMetaService> {

    private final DbTableRelationMetaService service;

    public DbTableRelationMetaController(DbTableRelationMetaService service) {
        this.service = service;
    }

    @Override
    protected DbTableRelationMetaService service() {
        return service;
    }
}
