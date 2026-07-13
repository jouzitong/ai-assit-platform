package ai.platform.aiassit.data.virtualization.data.service.impl;

import ai.platform.aiassit.data.virtualization.data.convert.FieldTransformRuleConvert;
import ai.platform.aiassit.data.virtualization.data.dto.FieldTransformRuleDTO;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformRuleEntity;
import ai.platform.aiassit.data.virtualization.data.mapper.FieldTransformRuleMapper;
import ai.platform.aiassit.data.virtualization.data.service.FieldTransformRuleService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class FieldTransformRuleServiceImpl extends BaseMapperService<FieldTransformRuleEntity, FieldTransformRuleMapper, FieldTransformRuleDTO>
        implements FieldTransformRuleService {
    private final FieldTransformRuleConvert convert;
    public FieldTransformRuleServiceImpl(FieldTransformRuleConvert convert) { this.convert = convert; }
    @Override protected IConvert<FieldTransformRuleEntity, FieldTransformRuleDTO> convert() { return convert; }
}
