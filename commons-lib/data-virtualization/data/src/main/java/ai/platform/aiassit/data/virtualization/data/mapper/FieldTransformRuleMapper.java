package ai.platform.aiassit.data.virtualization.data.mapper;

import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface FieldTransformRuleMapper extends CrudMapper<FieldTransformRuleEntity> {
}
