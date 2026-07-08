package ai.platform.aiassit.render.data.component.mapper;

import ai.platform.aiassit.render.data.component.entity.RenderComponentContentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface RenderComponentContentMapper extends CrudMapper<RenderComponentContentEntity> {

    @Select("""
            SELECT *
            FROM render_component_content
            WHERE component_key = #{componentKey}
            LIMIT 1
            """)
    RenderComponentContentEntity selectByComponentKey(String componentKey);
}
