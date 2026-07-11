package ai.platform.aiassit.user.security.management.mapper;

import ai.platform.aiassit.user.security.management.entity.SecUserRoleManagementEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

import java.util.List;

@Mapper
public interface SecUserRoleManagementMapper extends CrudMapper<SecUserRoleManagementEntity> {

    @Select("""
            SELECT id, user_id, role_code
            FROM sec_user_role
            WHERE user_id = #{userId} AND role_code = #{roleCode}
            LIMIT 1
            """)
    SecUserRoleManagementEntity selectByUserIdAndRoleCode(@Param("userId") Long userId,
                                                           @Param("roleCode") String roleCode);

    @Select("""
            SELECT id, user_id, role_code
            FROM sec_user_role
            WHERE user_id = #{userId}
            ORDER BY id ASC
            """)
    List<SecUserRoleManagementEntity> selectByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM sec_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
