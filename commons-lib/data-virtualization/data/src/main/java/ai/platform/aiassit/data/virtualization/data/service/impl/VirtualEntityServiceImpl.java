package ai.platform.aiassit.data.virtualization.data.service.impl;

import ai.platform.aiassit.data.virtualization.data.convert.VirtualEntityConvert;
import ai.platform.aiassit.data.virtualization.data.dto.VirtualEntityDTO;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import ai.platform.aiassit.data.virtualization.data.mapper.VirtualEntityMapper;
import ai.platform.aiassit.data.virtualization.data.service.VirtualEntityService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class VirtualEntityServiceImpl extends BaseMapperService<VirtualEntityEntity, VirtualEntityMapper, VirtualEntityDTO>
        implements VirtualEntityService {
    private final VirtualEntityConvert convert;
    public VirtualEntityServiceImpl(VirtualEntityConvert convert) { this.convert = convert; }
    @Override protected IConvert<VirtualEntityEntity, VirtualEntityDTO> convert() { return convert; }
}
