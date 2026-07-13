package ai.platform.aiassit.data.virtualization.data.mapper;

import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformPortEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface FieldTransformPortMapper extends CrudMapper<FieldTransformPortEntity> {
}
