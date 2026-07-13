package ai.platform.aiassit.data.virtualization.data.service.impl;

import ai.platform.aiassit.data.virtualization.data.convert.VirtualBindingConvert;
import ai.platform.aiassit.data.virtualization.data.dto.VirtualBindingDTO;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualBindingEntity;
import ai.platform.aiassit.data.virtualization.data.mapper.VirtualBindingMapper;
import ai.platform.aiassit.data.virtualization.data.service.VirtualBindingService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class VirtualBindingServiceImpl extends BaseMapperService<VirtualBindingEntity, VirtualBindingMapper, VirtualBindingDTO>
        implements VirtualBindingService {
    private final VirtualBindingConvert convert;
    public VirtualBindingServiceImpl(VirtualBindingConvert convert) { this.convert = convert; }
    @Override protected IConvert<VirtualBindingEntity, VirtualBindingDTO> convert() { return convert; }
}
