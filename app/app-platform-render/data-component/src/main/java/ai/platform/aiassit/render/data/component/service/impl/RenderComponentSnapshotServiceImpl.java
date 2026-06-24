package ai.platform.aiassit.render.data.component.service.impl;

import ai.platform.aiassit.render.data.component.convert.RenderComponentSnapshotConvert;
import ai.platform.aiassit.render.data.component.entity.RenderComponentSnapshotEntity;
import ai.platform.aiassit.render.data.component.entity.dto.RenderComponentSnapshotDTO;
import ai.platform.aiassit.render.data.component.mapper.RenderComponentSnapshotMapper;
import ai.platform.aiassit.render.data.component.service.RenderComponentSnapshotService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class RenderComponentSnapshotServiceImpl
        extends BaseMapperService<RenderComponentSnapshotEntity, RenderComponentSnapshotMapper, RenderComponentSnapshotDTO>
        implements RenderComponentSnapshotService {

    private final RenderComponentSnapshotConvert convert;

    public RenderComponentSnapshotServiceImpl(RenderComponentSnapshotConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<RenderComponentSnapshotEntity, RenderComponentSnapshotDTO> convert() {
        return convert;
    }
}
