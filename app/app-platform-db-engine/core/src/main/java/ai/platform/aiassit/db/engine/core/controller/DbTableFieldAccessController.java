package ai.platform.aiassit.db.engine.core.controller;

import ai.platform.aiassit.db.engine.api.DbTableFieldMetaApi;
import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaDeleteRequest;
import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.core.service.DbTableFieldAccessService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 物理表字段元数据的内部访问接口。
 *
 * <p>供平台内部按数据源、表和字段定位已登记的字段元数据，避免调用方直接依赖物理数据库的结构细节。</p>
 */
@RestController
@RequestMapping
public class DbTableFieldAccessController implements DbTableFieldMetaApi {

    private final DbTableFieldAccessService service;

    public DbTableFieldAccessController(DbTableFieldAccessService service) {
        this.service = service;
    }

    /**
     * 按条件查询已登记表字段元数据。
     *
     * @param request 查询请求体，包含数据源、表名、字段名等定位条件
     * @return 匹配的字段元数据列表
     */
    @Override
    public List<DbTableFieldMetaDTO> list(DbTableFieldMetaQueryRequest request) {
        return service.list(request);
    }

    /**
     * 查询一条确定的表字段元数据。
     *
     * @param request 查询请求体，提供唯一字段定位条件
     * @return 字段元数据详情；不存在时由服务层按约定处理
     */
    @Override
    public DbTableFieldMetaDTO get(DbTableFieldMetaQueryRequest request) {
        return service.get(request);
    }

    /**
     * 新增一条已登记的表字段元数据。
     *
     * @param dto 字段元数据请求体，包含数据源、表、列及其结构属性
     * @return 创建后的字段元数据
     */
    @Override
    public DbTableFieldMetaDTO create(DbTableFieldMetaDTO dto) {
        return service.create(dto);
    }

    /**
     * 更新一条已登记的表字段元数据。
     *
     * @param dto 字段元数据请求体，包含记录标识和需更新的结构或管理属性
     * @return 更新后的字段元数据
     */
    @Override
    public DbTableFieldMetaDTO update(DbTableFieldMetaDTO dto) {
        return service.update(dto);
    }

    /**
     * 删除一条已登记的表字段元数据。
     *
     * @param request 删除请求体，包含待删除字段的定位信息
     * @return 是否成功删除
     */
    @Override
    public Boolean delete(DbTableFieldMetaDeleteRequest request) {
        return service.delete(request);
    }
}
