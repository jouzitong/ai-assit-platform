package ai.platform.aiassit.render.data.render.service.impl;

import ai.platform.aiassit.render.data.render.convert.RenderPageCategoryConvert;
import ai.platform.aiassit.render.data.render.entity.RenderPageCategoryEntity;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageCategoryDTO;
import ai.platform.aiassit.render.data.render.mapper.RenderPageCategoryMapper;
import ai.platform.aiassit.render.data.render.service.RenderPageCategoryService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class RenderPageCategoryServiceImpl
        extends BaseMapperService<RenderPageCategoryEntity, RenderPageCategoryMapper, RenderPageCategoryDTO>
        implements RenderPageCategoryService {

    private final RenderPageCategoryConvert convert;

    public RenderPageCategoryServiceImpl(RenderPageCategoryConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<RenderPageCategoryEntity, RenderPageCategoryDTO> convert() {
        return convert;
    }
}
