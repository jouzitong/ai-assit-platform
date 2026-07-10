package ai.platform.aiassit.render.data.render.mapper;

import ai.platform.aiassit.render.data.render.entity.RenderPageContentEntity;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface RenderPageContentMapper extends CrudMapper<RenderPageContentEntity> {

    @Results(id = "RenderPageContentResultMap", value = {
            @Result(column = "page_code", property = "pageCode"),
            @Result(column = "content", property = "content", typeHandler = JacksonTypeHandler.class)
    })
    @Select("""
            SELECT *
            FROM render_page_content
            WHERE page_code = #{pageCode}
            LIMIT 1
            """)
    RenderPageContentEntity selectByPageCode(String pageCode);
}
