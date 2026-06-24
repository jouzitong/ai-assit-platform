package ai.platform.aiassit.render.data.component.entity;

import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

/**
 * 渲染组件主实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "render_component", autoResultMap = true)
public class RenderComponentEntity extends AuditableEntity {

    /** 组件唯一标识。 */
    @TableField("`key`")
    private String key;

    /** 组件名称。 */
    @TableField("name")
    private String name;

    /** 组件分类。 */
    @TableField("category")
    private String category;

    /** 组件状态。 */
    @TableField(value = "status", typeHandler = DefaultEnumTypeHandler.class)
    private EffectiveStatus status;
}
