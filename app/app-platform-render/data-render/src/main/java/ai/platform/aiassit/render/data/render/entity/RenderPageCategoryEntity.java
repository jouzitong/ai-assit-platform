package ai.platform.aiassit.render.data.render.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

/**
 * 渲染页面分类实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("render_page_category")
public class RenderPageCategoryEntity extends AuditableEntity {

    /** 分类编码。 */
    @JdbcColumn(
            name = "code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "分类编码"
    )
    @TableField("code")
    private String code;

    /** 分类名称。 */
    @JdbcColumn(
            name = "name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "分类名称"
    )
    @TableField("name")
    private String name;

    /** 父分类编码。 */
    @JdbcColumn(
            name = "parent_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "父分类编码，根分类为空"
    )
    @TableField("parent_code")
    private String parentCode;

    /** 分类路径。 */
    @JdbcColumn(
            name = "path",
            dataType = "VARCHAR(512)",
            length = 512,
            nullable = false,
            comment = "分类路径"
    )
    @TableField("path")
    private String path;

    /** 排序号。 */
    @JdbcColumn(
            name = "sort_no",
            dataType = "INT",
            nullable = false,
            defaultValue = "0",
            comment = "排序号"
    )
    @TableField("sort_no")
    private Integer sortNo;

    /** 是否启用。 */
    @JdbcColumn(
            name = "enabled",
            dataType = "BOOLEAN",
            nullable = false,
            defaultValue = "TRUE",
            comment = "是否启用"
    )
    @TableField("enabled")
    private Boolean enabled;
}
