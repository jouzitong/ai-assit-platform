package ai.platform.aiassit.render.data.render.service.impl;

import ai.platform.aiassit.render.data.render.convert.RenderPageContentConvert;
import ai.platform.aiassit.render.data.render.entity.RenderPageContentEntity;
import ai.platform.aiassit.render.data.render.entity.RenderPageContentEntity;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageContentDTO;
import ai.platform.aiassit.render.data.render.mapper.RenderPageContentMapper;
import ai.platform.aiassit.render.data.render.service.RenderPageContentService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class RenderPageContentServiceImpl
        extends BaseMapperService<RenderPageContentEntity, RenderPageContentMapper, RenderPageContentDTO>
        implements RenderPageContentService {

    private final RenderPageContentConvert convert;

    public RenderPageContentServiceImpl(RenderPageContentConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<RenderPageContentEntity, RenderPageContentDTO> convert() {
        return convert;
    }

    @Override
    public RenderPageContentDTO queryByPageCode(String pageCode) {
        RenderPageContentEntity entity = baseMapper.selectByPageCode(pageCode);
        return entity == null ? null : convert.toDTO(entity);
    }
}
