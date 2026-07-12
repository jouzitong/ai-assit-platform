package ai.platform.aiassit.render.data.component.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

/**
 * 渲染组件快照实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("render_component_snapshot")
public class RenderComponentSnapshotEntity extends AuditableEntity {

    /** 组件唯一标识。 */
    @JdbcColumn(
            name = "component_key",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            comment = "组件唯一标识"
    )
    @TableField("component_key")
    private String componentKey;

    /** 组件说明文档快照。 */
    @JdbcColumn(
            name = "doc_markdown",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "组件说明文档快照"
    )
    @TableField("doc_markdown")
    private String docMarkdown;

    /** 组件示例 JSON 快照。 */
    @JdbcColumn(
            name = "example_json",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "组件示例JSON快照"
    )
    @TableField("example_json")
    private String exampleJson;
}
