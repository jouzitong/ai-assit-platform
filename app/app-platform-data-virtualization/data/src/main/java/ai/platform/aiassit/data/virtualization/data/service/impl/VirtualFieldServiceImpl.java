package ai.platform.aiassit.data.virtualization.data.service.impl;

import ai.platform.aiassit.data.virtualization.data.convert.VirtualFieldConvert;
import ai.platform.aiassit.data.virtualization.data.dto.VirtualFieldDTO;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualFieldEntity;
import ai.platform.aiassit.data.virtualization.data.mapper.VirtualFieldMapper;
import ai.platform.aiassit.data.virtualization.data.service.VirtualFieldService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class VirtualFieldServiceImpl extends BaseMapperService<VirtualFieldEntity, VirtualFieldMapper, VirtualFieldDTO>
        implements VirtualFieldService {
    private final VirtualFieldConvert convert;
    public VirtualFieldServiceImpl(VirtualFieldConvert convert) { this.convert = convert; }
    @Override protected IConvert<VirtualFieldEntity, VirtualFieldDTO> convert() { return convert; }
}
