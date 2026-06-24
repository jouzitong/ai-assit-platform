package ai.platform.aiassit.render.data.component.service.impl;

import ai.platform.aiassit.render.data.component.convert.RenderComponentConvert;
import ai.platform.aiassit.render.data.component.entity.RenderComponentEntity;
import ai.platform.aiassit.render.data.component.entity.dto.RenderComponentDTO;
import ai.platform.aiassit.render.data.component.mapper.RenderComponentMapper;
import ai.platform.aiassit.render.data.component.service.RenderComponentService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class RenderComponentServiceImpl
        extends BaseMapperService<RenderComponentEntity, RenderComponentMapper, RenderComponentDTO>
        implements RenderComponentService {

    private final RenderComponentConvert convert;

    public RenderComponentServiceImpl(RenderComponentConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<RenderComponentEntity, RenderComponentDTO> convert() {
        return convert;
    }
}
