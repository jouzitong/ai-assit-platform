package ai.platform.aiassit.db.engine.meta.controller;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableRelationMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableRelationMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableRelationMetaService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物理表关联元数据的通用 CRUD 接口。
 *
 * <p>复用 {@link BaseController} 的新增、详情、分页、更新和删除能力；请求体使用 {@link DbTableRelationMetaDTO}，查询条件使用
 * {@link DbTableRelationMetaQueryRequest}，用于维护表间关系的结构化描述。</p>
 */
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
