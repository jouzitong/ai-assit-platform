package ai.platform.aiassit.user.security.management.mapper;

import ai.platform.aiassit.user.security.management.entity.SecUserManagementEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface SecUserManagementMapper extends CrudMapper<SecUserManagementEntity> {

    @Select("""
            SELECT id, username, display_name, status, tenant_id
            FROM sec_user
            WHERE username = #{username}
            LIMIT 1
            """)
    SecUserManagementEntity selectByUsername(@Param("username") String username);
}
