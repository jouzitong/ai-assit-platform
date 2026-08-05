package ai.platform.aiassit.db.engine.meta.controller;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableIndexMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableIndexMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableIndexMetaService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物理表索引元数据的通用 CRUD 接口。
 *
 * <p>复用 {@link BaseController} 的新增、详情、分页、更新和删除能力；请求体使用 {@link DbTableIndexMetaDTO}，查询条件使用
 * {@link DbTableIndexMetaQueryRequest}，用于维护同步后的索引结构及其启用状态。</p>
 */
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
