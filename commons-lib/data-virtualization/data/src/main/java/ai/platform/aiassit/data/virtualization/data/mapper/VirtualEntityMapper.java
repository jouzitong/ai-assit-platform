package ai.platform.aiassit.data.virtualization.data.mapper;

import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface VirtualEntityMapper extends CrudMapper<VirtualEntityEntity> {
}
