package ai.platform.aiassit.render.data.render.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

import java.util.Map;

/**
 * 渲染页面快照实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "render_page_snapshot", autoResultMap = true)
public class RenderPageSnapshotEntity extends AuditableEntity {

    /** 页面编码。 */
    @JdbcColumn(name = "page_code", comment = "页面编码。")
    @TableField("page_code")
    private String pageCode;

    /** 快照业务版本号。 */
    @JdbcColumn(name = "snapshot_version", comment = "快照业务版本号。")
    @TableField("snapshot_version")
    private Integer snapshotVersion;

    /** 快照描述。 */
    @JdbcColumn(name = "description", comment = "快照描述。")
    @TableField("description")
    private String description;

    /** 页面 JSON 快照。 */
    @JdbcColumn(name = "content", comment = "页面 JSON 快照。")
    @TableField(value = "content", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> content;
}
