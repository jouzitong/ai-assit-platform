package ai.platform.aiassit.render.data.component.mapper;

import ai.platform.aiassit.render.data.component.entity.RenderComponentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface RenderComponentMapper extends CrudMapper<RenderComponentEntity> {

    @Select("""
            SELECT *
            FROM render_component
            WHERE `key` = #{key}
              AND deleted = 0
            LIMIT 1
            """)
    RenderComponentEntity selectByKey(String key);
}
