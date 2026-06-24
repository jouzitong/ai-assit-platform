package ai.platform.aiassit.render.data.render.mapper;

import ai.platform.aiassit.render.data.render.entity.RenderPageCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface RenderPageCategoryMapper extends CrudMapper<RenderPageCategoryEntity> {
}
