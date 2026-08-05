package ai.platform.aiassit.db.engine.meta.controller;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableFieldMetaService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物理表字段元数据的通用 CRUD 接口。
 *
 * <p>复用 {@link BaseController} 的通用接口维护已同步字段的结构与管理属性；请求体使用 {@link DbTableFieldMetaDTO}，查询条件使用
 * {@link DbTableFieldMetaQueryRequest}。</p>
 */
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
