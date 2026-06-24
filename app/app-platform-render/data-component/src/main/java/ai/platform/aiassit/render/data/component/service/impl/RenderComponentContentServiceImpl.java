package ai.platform.aiassit.render.data.component.service.impl;

import ai.platform.aiassit.render.data.component.convert.RenderComponentContentConvert;
import ai.platform.aiassit.render.data.component.entity.RenderComponentContentEntity;
import ai.platform.aiassit.render.data.component.entity.dto.RenderComponentContentDTO;
import ai.platform.aiassit.render.data.component.mapper.RenderComponentContentMapper;
import ai.platform.aiassit.render.data.component.service.RenderComponentContentService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class RenderComponentContentServiceImpl
        extends BaseMapperService<RenderComponentContentEntity, RenderComponentContentMapper, RenderComponentContentDTO>
        implements RenderComponentContentService {

    private final RenderComponentContentConvert convert;

    public RenderComponentContentServiceImpl(RenderComponentContentConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<RenderComponentContentEntity, RenderComponentContentDTO> convert() {
        return convert;
    }

    @Override
    public RenderComponentContentDTO queryByComponentKey(String componentKey) {
        RenderComponentContentEntity entity = baseMapper.selectByComponentKey(componentKey);
        return entity == null ? null : convert.toDTO(entity);
    }
}
