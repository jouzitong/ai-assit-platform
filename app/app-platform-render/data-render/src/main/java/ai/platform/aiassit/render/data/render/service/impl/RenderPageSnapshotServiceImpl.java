package ai.platform.aiassit.render.data.render.service.impl;

import ai.platform.aiassit.render.data.render.convert.RenderPageSnapshotConvert;
import ai.platform.aiassit.render.data.render.entity.RenderPageSnapshotEntity;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageSnapshotDTO;
import ai.platform.aiassit.render.data.render.mapper.RenderPageSnapshotMapper;
import ai.platform.aiassit.render.data.render.service.RenderPageSnapshotService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class RenderPageSnapshotServiceImpl
        extends BaseMapperService<RenderPageSnapshotEntity, RenderPageSnapshotMapper, RenderPageSnapshotDTO>
        implements RenderPageSnapshotService {

    private final RenderPageSnapshotConvert convert;

    public RenderPageSnapshotServiceImpl(RenderPageSnapshotConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<RenderPageSnapshotEntity, RenderPageSnapshotDTO> convert() {
        return convert;
    }

    @Override
    public Integer nextSnapshotVersion(String pageCode) {
        Integer maxVersion = baseMapper.selectMaxSnapshotVersion(pageCode);
        return maxVersion == null ? 1 : maxVersion + 1;
    }
}
