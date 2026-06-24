package ai.platform.aiassit.render.data.render.service.impl;

import ai.platform.aiassit.render.data.render.convert.RenderPageConvert;
import ai.platform.aiassit.render.data.render.entity.RenderPageEntity;
import ai.platform.aiassit.render.data.render.entity.RenderPageEntity;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageDTO;
import ai.platform.aiassit.render.data.render.mapper.RenderPageMapper;
import ai.platform.aiassit.render.data.render.service.RenderPageService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class RenderPageServiceImpl
        extends BaseMapperService<RenderPageEntity, RenderPageMapper, RenderPageDTO>
        implements RenderPageService {

    private final RenderPageConvert convert;

    public RenderPageServiceImpl(RenderPageConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<RenderPageEntity, RenderPageDTO> convert() {
        return convert;
    }

    @Override
    public RenderPageDTO queryByCode(String code) {
        RenderPageEntity entity = baseMapper.selectByCode(code);
        return entity == null ? null : convert.toDTO(entity);
    }
}
