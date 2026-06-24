package ai.platform.aiassit.render.data.render.mapper;

import ai.platform.aiassit.render.data.render.entity.RenderPageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface RenderPageMapper extends CrudMapper<RenderPageEntity> {

    @Select("""
            SELECT *
            FROM render_page
            WHERE code = #{code}
              AND deleted = 0
            LIMIT 1
            """)
    RenderPageEntity selectByCode(String code);
}
