package ai.platform.aiassit.render.data.render.mapper;

import ai.platform.aiassit.render.data.render.entity.RenderPageCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface RenderPageCategoryMapper extends CrudMapper<RenderPageCategoryEntity> {

    @Select("""
            SELECT *
            FROM render_page_category
            WHERE code = #{code}
              AND deleted = 0
            LIMIT 1
            """)
    RenderPageCategoryEntity selectByCode(String code);
}
