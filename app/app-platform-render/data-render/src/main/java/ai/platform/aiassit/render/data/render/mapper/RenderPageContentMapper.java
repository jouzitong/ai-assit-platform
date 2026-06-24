package ai.platform.aiassit.render.data.render.mapper;

import ai.platform.aiassit.render.data.render.entity.RenderPageContentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface RenderPageContentMapper extends CrudMapper<RenderPageContentEntity> {

    @Select("""
            SELECT *
            FROM render_page_content
            WHERE page_code = #{pageCode}
            LIMIT 1
            """)
    RenderPageContentEntity selectByPageCode(String pageCode);
}
