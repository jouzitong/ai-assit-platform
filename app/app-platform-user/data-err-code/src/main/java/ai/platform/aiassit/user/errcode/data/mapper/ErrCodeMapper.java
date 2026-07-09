package ai.platform.aiassit.user.errcode.data.mapper;

import ai.platform.aiassit.user.errcode.data.entity.ErrCodeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface ErrCodeMapper extends CrudMapper<ErrCodeEntity> {

    @Select("""
            SELECT id, code, http_status, description, tags,
                   create_time, update_time, created_by, updated_by, version
            FROM err_code
            WHERE code = #{code}
            LIMIT 1
            """)
    ErrCodeEntity selectByCode(Integer code);
}
