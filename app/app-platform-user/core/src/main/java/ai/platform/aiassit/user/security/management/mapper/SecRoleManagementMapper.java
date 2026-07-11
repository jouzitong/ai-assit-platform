package ai.platform.aiassit.user.security.management.mapper;

import ai.platform.aiassit.user.security.management.entity.SecRoleManagementEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface SecRoleManagementMapper extends CrudMapper<SecRoleManagementEntity> {

    @Select("""
            SELECT id, role_code, role_name, status
            FROM sec_role
            WHERE role_code = #{roleCode}
            LIMIT 1
            """)
    SecRoleManagementEntity selectByRoleCode(@Param("roleCode") String roleCode);
}
