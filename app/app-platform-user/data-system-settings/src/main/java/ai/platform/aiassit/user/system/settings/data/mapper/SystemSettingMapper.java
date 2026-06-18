package ai.platform.aiassit.user.system.settings.data.mapper;

import ai.platform.aiassit.user.system.settings.data.entity.SystemSettingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface SystemSettingMapper extends CrudMapper<SystemSettingEntity> {

    @Select("""
            SELECT id, setting_key, description, setting_value, value_type, enabled,
                   create_time, update_time, created_by, updated_by, version
            FROM system_settings
            WHERE setting_key = #{settingKey}
            LIMIT 1
            """)
    SystemSettingEntity selectBySettingKey(String settingKey);
}
