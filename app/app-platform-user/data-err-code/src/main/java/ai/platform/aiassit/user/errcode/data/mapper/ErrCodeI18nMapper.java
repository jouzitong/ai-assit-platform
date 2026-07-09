package ai.platform.aiassit.user.errcode.data.mapper;

import ai.platform.aiassit.user.errcode.data.entity.ErrCodeI18nEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface ErrCodeI18nMapper extends CrudMapper<ErrCodeI18nEntity> {

    @Select("""
            SELECT id, err_code, locale, message_template, description,
                   create_time, update_time, created_by, updated_by, version
            FROM err_code_i18n
            WHERE err_code = #{errCode}
              AND locale = #{locale}
            LIMIT 1
            """)
    ErrCodeI18nEntity selectByErrCodeAndLocale(@Param("errCode") Integer errCode, @Param("locale") String locale);

    @Select("""
            SELECT id, err_code, locale, message_template, description,
                   create_time, update_time, created_by, updated_by, version
            FROM err_code_i18n
            WHERE err_code = #{errCode}
            ORDER BY CASE locale WHEN 'zh-CN' THEN 0 WHEN 'en-US' THEN 1 ELSE 2 END, id ASC
            LIMIT 1
            """)
    ErrCodeI18nEntity selectFirstByErrCode(Integer errCode);
}
