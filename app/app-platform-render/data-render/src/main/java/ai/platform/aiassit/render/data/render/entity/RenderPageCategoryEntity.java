package ai.platform.aiassit.render.data.render.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

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
    @TableField("code")
    private String code;

    /** 分类名称。 */
    @TableField("name")
    private String name;

    /** 父分类编码。 */
    @TableField("parent_code")
    private String parentCode;

    /** 分类路径。 */
    @TableField("path")
    private String path;

    /** 排序号。 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 是否启用。 */
    @TableField("enabled")
    private Boolean enabled;
}
