package ai.platform.aiassit.render.data.render.entity;

import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

/**
 * 渲染页面主实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "render_page", autoResultMap = true)
public class RenderPageEntity extends AuditableEntity {

    /** 页面编码。 */
    @JdbcColumn(name = "code", unique = true, comment = "页面编码。")
    @TableField("code")
    private String code;

    /** 页面名称。 */
    @JdbcColumn(name = "name", comment = "页面名称。")
    @TableField("name")
    private String name;

    /** 所属分类编码，可为空。 */
    @JdbcColumn(name = "category_code", comment = "所属分类编码，可为空。")
    @TableField("category_code")
    private String categoryCode;

    /** 页面状态。 */
    @JdbcColumn(name = "status", comment = "页面状态。")
    @TableField(value = "status", typeHandler = DefaultEnumTypeHandler.class)
    private EffectiveStatus status;
}
