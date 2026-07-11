package ai.platform.aiassit.user.security.management.mapper;

import ai.platform.aiassit.user.security.management.entity.SecUserCredentialManagementEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface SecUserCredentialManagementMapper extends CrudMapper<SecUserCredentialManagementEntity> {

    @Select("""
            SELECT id, user_id, credential_type, password_hash, password_algo, password_salt
            FROM sec_user_credential
            WHERE user_id = #{userId} AND credential_type = 'PASSWORD'
            ORDER BY id ASC
            LIMIT 1
            """)
    SecUserCredentialManagementEntity selectPasswordByUserId(@Param("userId") Long userId);
}
