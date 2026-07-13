package ai.platform.aiassit.data.virtualization.data.service.impl;

import ai.platform.aiassit.data.virtualization.data.convert.VirtualRelationConvert;
import ai.platform.aiassit.data.virtualization.data.dto.VirtualRelationDTO;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualRelationEntity;
import ai.platform.aiassit.data.virtualization.data.mapper.VirtualRelationMapper;
import ai.platform.aiassit.data.virtualization.data.service.VirtualRelationService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class VirtualRelationServiceImpl extends BaseMapperService<VirtualRelationEntity, VirtualRelationMapper, VirtualRelationDTO>
        implements VirtualRelationService {
    private final VirtualRelationConvert convert;
    public VirtualRelationServiceImpl(VirtualRelationConvert convert) { this.convert = convert; }
    @Override protected IConvert<VirtualRelationEntity, VirtualRelationDTO> convert() { return convert; }
}
