package ai.platform.aiassit.data.virtualization.data.service.impl;

import ai.platform.aiassit.data.virtualization.data.convert.FieldTransformPortConvert;
import ai.platform.aiassit.data.virtualization.data.dto.FieldTransformPortDTO;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformPortEntity;
import ai.platform.aiassit.data.virtualization.data.mapper.FieldTransformPortMapper;
import ai.platform.aiassit.data.virtualization.data.service.FieldTransformPortService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class FieldTransformPortServiceImpl extends BaseMapperService<FieldTransformPortEntity, FieldTransformPortMapper, FieldTransformPortDTO>
        implements FieldTransformPortService {
    private final FieldTransformPortConvert convert;
    public FieldTransformPortServiceImpl(FieldTransformPortConvert convert) { this.convert = convert; }
    @Override protected IConvert<FieldTransformPortEntity, FieldTransformPortDTO> convert() { return convert; }
}
