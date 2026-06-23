package ai.platform.aiassit.db.engine.core.controller;

import ai.platform.aiassit.db.engine.api.DbTableFieldMetaApi;
import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaDeleteRequest;
import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.core.service.DbTableFieldAccessService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
public class DbTableFieldAccessController implements DbTableFieldMetaApi {

    private final DbTableFieldAccessService service;

    public DbTableFieldAccessController(DbTableFieldAccessService service) {
        this.service = service;
    }

    @Override
    public List<DbTableFieldMetaDTO> list(DbTableFieldMetaQueryRequest request) {
        return service.list(request);
    }

    @Override
    public DbTableFieldMetaDTO get(DbTableFieldMetaQueryRequest request) {
        return service.get(request);
    }

    @Override
    public DbTableFieldMetaDTO create(DbTableFieldMetaDTO dto) {
        return service.create(dto);
    }

    @Override
    public DbTableFieldMetaDTO update(DbTableFieldMetaDTO dto) {
        return service.update(dto);
    }

    @Override
    public Boolean delete(DbTableFieldMetaDeleteRequest request) {
        return service.delete(request);
    }
}
