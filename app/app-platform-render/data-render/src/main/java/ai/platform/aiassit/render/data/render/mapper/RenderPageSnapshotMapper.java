package ai.platform.aiassit.render.data.render.mapper;

import ai.platform.aiassit.render.data.render.entity.RenderPageSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface RenderPageSnapshotMapper extends CrudMapper<RenderPageSnapshotEntity> {

    @Select("""
            SELECT COALESCE(MAX(snapshot_version), 0)
            FROM render_page_snapshot
            WHERE page_code = #{pageCode}
            """)
    Integer selectMaxSnapshotVersion(String pageCode);
}
