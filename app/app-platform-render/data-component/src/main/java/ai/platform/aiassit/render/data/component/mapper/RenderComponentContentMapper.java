package ai.platform.aiassit.render.data.component.mapper;

import ai.platform.aiassit.render.data.component.entity.RenderComponentContentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface RenderComponentContentMapper extends CrudMapper<RenderComponentContentEntity> {
}
