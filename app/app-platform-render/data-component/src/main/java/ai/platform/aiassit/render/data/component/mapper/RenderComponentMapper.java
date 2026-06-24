package ai.platform.aiassit.render.data.component.mapper;

import ai.platform.aiassit.render.data.component.entity.RenderComponentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface RenderComponentMapper extends CrudMapper<RenderComponentEntity> {
}
